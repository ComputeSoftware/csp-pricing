(ns dev
  (:require
    [clojure.core.async :as async]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [cognitect.aws.client.api :as aws]
    [compute.common.base64 :as base64]
    [compute.common.fs :as fs]
    [kwill.anomkit :as ak]
    [datomic.client.api :as d])
  (:import (datomic.dev_local.impl DurableClient)
           (java.io File)
           (java.lang ProcessBuilder$Redirect)
           (java.security DigestInputStream MessageDigest)
           (java.util.zip ZipEntry ZipInputStream ZipOutputStream)))


(set! *warn-on-reflection* true)
(def ^:dynamic *ci-storage-bucket* "dev-local-prices-dbs")
(def ^:dynamic *pricing-storage-bucket* "computesoftware-csp-pricing-files")


(def *s3
  (delay
    (aws/client {:api    :s3
                 :region "us-west-2"})))


(defn fetch-remote-prices
  [download-path]
  (let [ch-object (async/chan 1e4)
        ch-log (async/chan 1e4)
        *out (atom {})]
    (async/pipeline-blocking 5
      ch-log
      (map (fn [{:keys [Key] :as object}]
             (let [f (io/file Key)]
               (if (.exists f)
                 {:skip Key}
                 (let [{:keys [Body]
                        :as   response} (aws/invoke @*s3
                                          {:op      :GetObject
                                           :request {:Bucket *pricing-storage-bucket*
                                                     :Key    Key}})]
                   (if Body
                     (with-open [out (do
                                       (.mkdirs (.getParentFile f))
                                       (io/output-stream f))]
                       (io/copy Body out)
                       {:download Key})
                     {:error Key :msg response}))))))
      ch-object)
    (loop [request {:Bucket *pricing-storage-bucket*
                    :Prefix (str download-path "/")}]
      (let [{:keys [Contents NextContinuationToken]} (aws/invoke @*s3
                                                       {:op      :ListObjectsV2
                                                        :request request})]
        (doseq [object Contents]
          (async/>!! ch-object object))
        (if NextContinuationToken
          (recur (assoc request :ContinuationToken NextContinuationToken))
          (async/close! ch-object))))
    (loop []
      (when-let [{:keys [skip download]
                  :as   opts} (async/<!! ch-log)]
        (cond
          skip (swap! *out update :skip
                 (fnil conj []) skip)
          download (swap! *out update :download
                     (fnil conj []) download)
          :else (do (prn opts)
                  (swap! *out update :error
                    (fnil conj [])
                    opts)))
        (recur)))
    @*out))

(defn to-hex-string
  [bs]
  (let [hex-string (StringBuilder.)]
    (str (reduce (fn [^StringBuilder acc b]
                   (let [hex (Integer/toHexString (bit-and 0xFF b))]
                     (if (== 1 (count hex))
                       (.append (.append acc "0") hex)
                       (.append acc hex))))
           hex-string
           bs))))

(defn base64-digest
  "
  To upload, send `:ContentMD5` with the MD5 as base64 string
  "
  [algorithm x]
  (with-open [in (io/input-stream x)
              din (DigestInputStream. in (MessageDigest/getInstance algorithm))]
    (slurp din)
    (base64/encode (.digest (.getMessageDigest din)))))


(defn to-hex-digest
  "
  To check, compare MD5 hex with ETag key
  "
  [algorithm x]
  (with-open [in (io/input-stream x)
              din (DigestInputStream. in (MessageDigest/getInstance algorithm))]
    (slurp din)
    (to-hex-string (.digest (.getMessageDigest din)))))




(defn upload-prod-services
  [prod-services download-path]
  (let [ch-files-to-upload (async/chan 1e4)
        ch-out (async/chan 1e4)
        *out (atom {})]
    (async/pipeline-blocking 3
      ch-out
      (map (fn [^File f]
             (if (.exists f)
               (let [path (.getPath f)
                     {:keys                     [ContentLength ETag]
                      :cognitect.anomalies/keys [category]} (aws/invoke @*s3
                                                              {:op      :HeadObject
                                                               :request {:Bucket *pricing-storage-bucket*
                                                                         :Key    path}})
                     request {:Bucket     *pricing-storage-bucket*
                              :ContentMD5 (base64-digest "MD5" f)
                              :Key        path}]
                 (cond
                   (= category :cognitect.anomalies/not-found)
                   (with-open [in (io/input-stream f)]
                     (merge request
                       (aws/invoke @*s3 {:op      :PutObject
                                         :request (assoc request :Body in)})))
                   category {:path path :error category}
                   ;; we can do (= ETag (to-hex-digest f)) to check if the contet changed
                   (not (== ContentLength (.length f)))
                   {:path path :error :sizes-do-not-match}
                   :else {:skip path}))
               {:path (.getPath f) :error :missing-local})))
      ch-files-to-upload)
    (doseq [[srv version] prod-services
            f (cons
                (io/file download-path (namespace srv) (name srv) version "out"
                  (str (name srv) ".edn.gz"))
                (.listFiles (io/file download-path (namespace srv) (name srv) version "in")))]
      (async/>!! ch-files-to-upload f))
    (async/close! ch-files-to-upload)
    (loop []
      (when-let [{:keys [path error skip]
                  :as   opts} (async/<!! ch-out)]
        (cond
          error (swap! *out update :error assoc path error)
          skip (swap! *out update :skip (fnil conj []) skip)
          :else (swap! *out update :ok
                  (fnil conj [])
                  opts))
        (recur)))
    @*out))


(defn explain-data
  [conn]
  (let [db (d/db conn)
        history (d/history db)
        txs-with-service-config-doc (-> '[:find (count ?service-config-doc-tx)
                                          :where
                                          [?service-config :db/ident :cs.pricing-api.import/services-config]
                                          [?service-config :db/doc ?config ?service-config-doc-tx]]
                                      (d/q history)
                                      ffirst
                                      (or 0))
        txs-after-config (->> '[:find (count ?tx)
                                :where
                                [?service-config :db/ident :cs.pricing-api.import/services-config]
                                [?service-config :db/doc ?config ?service-config-doc-tx]
                                [?service-config-doc-tx :db/txInstant ?service-config-doc-inst]
                                [?tx :db/txInstant ?inst]
                                [(< ?service-config-doc-inst ?tx)]]
                           (d/q history)
                           ffirst
                           (or 0))
        problems (concat
                   (when-not (some-> txs-after-config zero?)
                     [{:message  (str "Found " (pr-str txs-after-config)
                                   " transactios after services-config transaction.")
                       :expected 0
                       :actual   txs-after-config}])
                   (when-not (some-> txs-with-service-config-doc (== 1))
                     [{:message  (str "Found " (pr-str txs-with-service-config-doc)
                                   " transactions with service-config.")
                       :expected 1
                       :actual   txs-with-service-config-doc}]))]
    (when (seq problems)
      {::value    db
       ::problems (vec problems)})))

(defn files-in-local-database
  [^DurableClient client {:keys [db-name]}]
  (seq (.listFiles (io/file (.-system_dir client) db-name))))

(defn relative-path
  [^File root ^File file]
  (if (= (.getCanonicalPath root)
        (.getCanonicalPath file))
    (.getName file)
    (let [x (.getParentFile file)]
      (str (when-let [p (relative-path root x)]
             (str p "/"))
        (.getName file)))))

(defn client->system-name
  [^DurableClient client]
  (.getName (io/file (.-system_dir client))))

(defn zip-pricing-to-ci
  [^DurableClient client {:keys [db-name]}]
  (let [root (io/file (.-system_dir client))
        system-name (client->system-name client)
        all-files (filter (fn [^File f]
                            (and
                              (.exists f)
                              (.isFile f)))
                    (file-seq root))
        target (.getCanonicalFile (io/file root ".." (str db-name ".zip")))]
    (when-not (== 8 (count all-files))
      (binding [*out* *err*]
        (println (str "WARNING: Too many files in the zip " (count all-files)))
        (flush)))
    (if (.exists target)
      (println (str "Already exists " (str target)))
      (do
        (println (str "Writing " (str target)))
        (fs/compress-directory-to-zip (io/file root db-name)
          target
          (fn [^File file]
            (let [relative-name (relative-path root file)
                  target-relative-name (string/replace relative-name
                                         (re-pattern (str "^" system-name "/" db-name))
                                         "prices/prices")]
              (println (str "Zipping " relative-name " as " target-relative-name))
              target-relative-name)))))
    target))

(defn trim-datomic
  [client {:keys [db-name]}]
  ;; (clojure.repl/doc datomic.dev-local/release-db)
  (let [cmd (into-array ["clojure" #_"-J-Dclojure.main.report=stderr" "-A:dev" "-M"
                         "-e" "(require '[datomic.client.api :as d])"
                         "-e" (pr-str (list 'd/connect (list 'd/client {:server-type :dev-local
                                                                        :system      (client->system-name client)})
                                        {:db-name db-name}))
                         "-e" "(System/exit 0)"])]
    (-> (java.lang.ProcessBuilder. ^"[Ljava.lang.String;" cmd)
      (.redirectOutput ProcessBuilder$Redirect/INHERIT)
      (.redirectError ProcessBuilder$Redirect/INHERIT)
      .start
      .waitFor)))

(defn upload-pricing-to-ci
  [client {:keys [db-name]
           :as   arg-map}]
  (loop []
    (let [n (count (files-in-local-database client arg-map))]
      (when (< 8 n)
        (println (str "Current files: " n " expected: 8. Trying to trim."))
        ;; event when it fails, it works. I don't know why.
        (println (trim-datomic client arg-map))
        (recur))))
  (println "Query the to build indexes")
  (d/q '[:find (max ?inst)
         :where
         [?e :db/txInstant ?inst]]
    (d/db (d/connect client arg-map)))
  (let [zip (zip-pricing-to-ci client arg-map)
        key (str db-name ".zip")
        last-modified (-> @*s3
                        (aws/invoke
                          {:op      :HeadObject
                           :request {:Bucket *ci-storage-bucket*
                                     :Key    key}})
                        :LastModified)]
    (if (inst? last-modified)
      (println (str (pr-str key) " is already uploaded. " (pr-str last-modified)))
      (do (println (str "Uploading " (str zip) " to s3://" *ci-storage-bucket* "/" key))
        (with-open [in (io/input-stream zip)]
          (aws/invoke @*s3
            {:op      :PutObject
             :request {:Key    key
                       :Bucket *ci-storage-bucket*
                       :Body   in}}))))))

(defn list-ci-dbs
  []
  (-> @*s3
    (aws/invoke
      {:op      :ListObjects
       :request {:Bucket *ci-storage-bucket*}})
    :Contents
    (->> (map :Key)
      (filter #(string/ends-with? % ".zip"))
      (map #(string/replace % #"\.zip$" "")))))
(comment
  (list-ci-dbs)
  (def client (d/client {:server-type :dev-local
                         :system      "prices"}))
  (def pricing-db-name "pricing-db-2022-02-22_1")
  (use-ci-db client {:db-name pricing-db-name})
  (def conn (d/connect client {:db-name pricing-db-name}))
  (d/q '[:find (max ?inst)
         :where
         [?e :db/txInstant ?inst]]
    (d/db conn)))

(defn rm-rf
  [^File f]
  (when (.isDirectory f)
    (doseq [f (.listFiles f)]
      (rm-rf f)))
  (.delete f))

(defn download
  [key ^File target]
  (if (.exists target)
    (println (str key " already downloaded"))
    (do
      (println (str "Downloading s3://" *ci-storage-bucket* "/" key " to " (.getCanonicalPath target)))
      (let [{:keys [Body]
             :as   result} (aws/invoke @*s3 {:op      :GetObject
                                             :request {:Key    key
                                                       :Bucket *ci-storage-bucket*}})]
        (ak/?! result {:key key :target target})
        (io/copy Body target)))))

;; https://github.com/clojure/tools.build/blob/master/src/main/clojure/clojure/tools/build/util/file.clj#L53
(defn cp-r
  [^File from ^File to]
  (.mkdirs to)
  (doseq [^File f (.listFiles from)]
    (io/copy f (io/file to (.getName f))))
  to)

(defn use-ci-db
  [^DurableClient client {:keys [db-name]}]
  (let [system-dir (io/file (.-system_dir client))
        storage-dir (io/file system-dir "..")
        key (str db-name ".zip")
        target (io/file storage-dir key)]
    (download key target)
    (if (.exists (io/file system-dir db-name))
      (println (str db-name " already extracted."))
      (with-open [zip (ZipInputStream. (io/input-stream (io/file storage-dir key)))]
        (loop []
          (when-let [entry (.getNextEntry zip)]
            (let [[_system-name _db-name & path] (string/split (.getName entry) #"/")
                  ^File target (apply io/file system-dir db-name path)]
              (.mkdirs (.getParentFile target))
              (println (str "Extracting " (.getName entry) " to " target))
              (io/copy zip target)
              (recur))))))
    (rm-rf (io/file system-dir "prices"))
    ;; make it into prices.
    ;; tests always run against prices
    (cp-r (io/file system-dir db-name)
      (io/file system-dir "prices"))))

(defn zip-system
  [^DurableClient client]
  (let [root (io/file (.-system_dir client))
        all-files (filter (fn [^File f]
                            (and
                              (.exists f)
                              (.isFile f)))
                    (file-seq root))
        lockfile (some (fn [^File f]
                         (when (= ".lock" (.getName f))
                           f))
                   all-files)
        target (io/file root ".." (str (.getName root) ".zip"))]
    #_(when lockfile
        (throw (ex-info (str "Found a lockfile " lockfile)
                 {})))
    (when (.exists target)
      (println (str "Overwriting " (str target) ".")))
    (with-open [zos (ZipOutputStream. (io/output-stream target))]
      (doseq [file all-files
              :let [relative-name (relative-path root file)]]
        (println (str "Zipping " relative-name " to " root))
        (.putNextEntry zos (ZipEntry. (str relative-name)))
        (io/copy file zos)
        (.closeEntry zos)))))

(comment
  ;; download from S3 old/current pricing files
  (fetch-remote-prices "pricing")

  (computesoftware.csp-pricing.dev/upload-prod-services prod-services download-path)

  ;; create pricing-db to CI. Only works for :dev-local clients.
  (computesoftware.csp-pricing.dev/upload-pricing-to-ci client {:db-name db-name-new})
  )


