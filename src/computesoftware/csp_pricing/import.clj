(ns computesoftware.csp-pricing.import
  (:require
    [ardoq.azure.api :as az]
    [clojure.core.async :as async]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.pprint :as pp]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [com.climate.claypoole :as pool]
    [compute.gcp.api :as api]
    [computesoftware.csp-pricing.aws.app-stream :as app-stream]
    [computesoftware.csp-pricing.aws.common :as aws]
    [computesoftware.csp-pricing.aws.dynamodb :as dynamodb]
    [computesoftware.csp-pricing.aws.ec2 :as ec2]
    [computesoftware.csp-pricing.aws.ecs-fargate :as ecs-fargate]
    [computesoftware.csp-pricing.aws.rds :as rds]
    [computesoftware.csp-pricing.aws.savings-plan :as savings-plan]
    [computesoftware.csp-pricing.aws.workspaces :as workspaces]
    [computesoftware.csp-pricing.azure.common :as azure]
    [computesoftware.csp-pricing.azure.managed-disks :as azure.md]
    [computesoftware.csp-pricing.azure.sql-database :as azure.sql-db]
    [computesoftware.csp-pricing.azure.vm :as azure.vm]
    [computesoftware.csp-pricing.azure.vm.license :as azure.vm.license]
    [computesoftware.csp-pricing.gcp.big-query :as big-query]
    [computesoftware.csp-pricing.gcp.ce-disk-image :as ce-disk-image]
    [computesoftware.csp-pricing.gcp.cloud-sql :as cloud-sql]
    [computesoftware.csp-pricing.gcp.common :as gcp]
    [computesoftware.csp-pricing.impl.json-reader :as json]
    [computesoftware.csp-pricing.impl.spec-helpers :as h]
    [datomic.client.api :as d]
    [datomic.client.api.async :as da]
    [kwill.anomkit :as ak]))

(def services
  {:aws/app-stream                  {:provider   :aws,
                                     :product-fn app-stream/product-entity-map,
                                     :price-fn   app-stream/price-entity-map,
                                     :url        "https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonAppStream/current/index.json"},
   :aws/ec2                         {:provider   :aws,
                                     :product-fn ec2/product-entity-map,
                                     :price-fn   ec2/price-entity-map,
                                     :url        "https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonEC2/current/index.json"},
   :aws/ecs-fargate                 {:provider   :aws,
                                     :product-fn ecs-fargate/product-entity-map,
                                     :price-fn   ecs-fargate/price-entity-map,
                                     :url        "https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonECS/current/index.json"},
   :aws/rds                         {:provider   :aws,
                                     :product-fn rds/product-entity-map,
                                     :price-fn   rds/price-entity-map,
                                     :url        "https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonRDS/current/index.json"}
   :aws/dynamodb                    {:provider   :aws,
                                     :product-fn dynamodb/product-entity-map,
                                     :price-fn   dynamodb/price-entity-map,
                                     :url        "https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonDynamoDB/current/index.json"},
   :aws/savings-plan                {:provider   :aws-savings-plan,
                                     :product-fn savings-plan/product-entity-map,
                                     :prices-fn  savings-plan/prices-entity-map,
                                     :base-url   "https://pricing.us-east-1.amazonaws.com",
                                     :url        "https://pricing.us-east-1.amazonaws.com/savingsPlan/v1.0/aws/AWSComputeSavingsPlan/current/region_index.json"},
   :aws/workspaces                  {:provider   :aws,
                                     :product-fn workspaces/product-entity-map,
                                     :price-fn   workspaces/price-entity-map,
                                     :url        "https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonWorkSpaces/current/index.json"},
   :azure/virtual-machines          {:provider     :azure,
                                     :meter-fn     azure.vm/do-meter,
                                     :aux          [:vms-specs],
                                     :service-name "Virtual Machines"},
   :azure/managed-disks             {:provider       :azure,
                                     :meter-fn       azure.md/do-meter,
                                     :service-name   "Storage"
                                     :sub-categories #{"Premium SSD Managed Disks"
                                                       "Standard HDD Managed Disks"
                                                       "Standard SSD Managed Disks"
                                                       "Ultra Disks"}}
   :azure/sql-database              {:provider     :azure,
                                     :meter-fn     azure.sql-db/do-meter,
                                     :service-name "SQL Database"}
   :azure.virtual-machines/licenses {:provider :azure, :meter-fn azure.vm.license/do-meter},
   :gcp/big-query                   {:provider     :gcp,
                                     :sku-fn       big-query/do-sku,
                                     :display-name "BigQuery"},
   :gcp/big-query-reservation       {:provider     :gcp,
                                     :sku-fn       big-query/do-sku,
                                     :display-name "BigQuery Reservation API"},
   :gcp/big-query-storage           {:provider     :gcp,
                                     :sku-fn       big-query/do-sku,
                                     :display-name "BigQuery Storage API"},
   :gcp/ce-disk-image               {:provider     :gcp,
                                     :sku-fn       ce-disk-image/do-sku,
                                     :display-name "Compute Engine"},
   :gcp/cloud-sql                   {:provider     :gcp,
                                     :sku-fn       cloud-sql/do-sku,
                                     :display-name "Cloud SQL"}})

(def schema
  (concat
    [#:db{:ident       :cs.pricing-api/tx
          :valueType   :db.type/ref
          :cardinality :db.cardinality/one}
     #:db{:ident       :cs.pricing-api.import/service
          :valueType   :db.type/keyword
          :cardinality :db.cardinality/one}]
    ;; AWS
    aws/schema
    ec2/schema
    app-stream/schema
    rds/schema
    workspaces/schema
    ecs-fargate/schema
    savings-plan/schema
    dynamodb/schema
    ;; GCP
    gcp/schema
    big-query/schema
    ce-disk-image/schema
    cloud-sql/schema
    ;; Azure
    azure/schema
    azure.vm/schema
    azure.md/schema
    azure.sql-db/schema
    azure.vm.license/schema))

(defn transact-schema [conn]
  (d/transact conn
    {:tx-data schema})
  :ok)

(defn- gcp-get [gcp-client op-map]
  (let [r (ak/?! (api/invoke gcp-client (assoc op-map :as :input-stream)))]
    (:input-stream r)))

(defprotocol GCPPagedResult
  (input [pi])
  (next-page [pi token]))

(extend-protocol GCPPagedResult
  Object
  (input [pi] (if (sequential? pi) (first pi) pi))
  (next-page [pi token] (when (sequential? pi) (next pi))))

(defn- gcp-result
  ([client op-map] (gcp-result client op-map nil))
  ([client op-map token]
   (reify GCPPagedResult
     (input [_]
       (gcp-get client (cond-> op-map token (assoc-in [:request :pageToken] token))))
     (next-page [_ token] (when (seq token) (gcp-result client op-map token))))))

(def download-path "pricing")

(defn ensure-directory!
  "Returns a directory (as a File instance) for the specified service, tag and step, creating it when missing."
  [download-path qualified-service tag step]
  (let [[dir subdir] (str/split (namespace qualified-service) #"\." 2)]
    (doto (io/file download-path dir (str (some-> subdir (str ".")) (name qualified-service)) tag step)
      .mkdirs)))

(defn list-downloaded-services
  "Lists service maps possible for a given download-path."
  [download-path]
  (->> (io/file download-path)
    (file-seq)
    (keep (fn [^java.io.File f]
            (when-let [[_ _ provider-str service-str date-str] (re-matches #"(.*)\/(.*)\/(.*)\/([0-9]*-[0-9]*)" (.getPath f))]
              {(keyword provider-str service-str) date-str})))
    (sort-by (comp key first))))

(defn open-gz-file-out
  "Takes a File instance pointing to a gzipped file and returns a compressing OutputStream."
  [^java.io.File file]
  (-> file
    java.io.FileOutputStream.
    ;; increase compression window-size
    (java.util.zip.GZIPOutputStream. 65536)
    ;; must feed GZIP in chunk big enough, otherwise the compression window is not used to its full extent
    #_(java.io.BufferedOutputStream. 65536)))

(defn dump-to-json-gz-file
  "Closes its input on completion.
  Returns a File instance of the written-to file."
  [in dir basename]
  (let [file (java.io.File. dir (str basename ".json.gz"))]
    (with-open [out (open-gz-file-out file)
                in (if (string? in) (-> in (.getBytes "UTF-8") java.io.ByteArrayInputStream.) in)]
      (io/copy in out))
    file))

(defn open-gz-file-in
  "Takes a File instance pointing to a gzipped file and returns an uncompressed InputStream."
  [^java.io.File file]
  (-> file
    java.io.FileInputStream.
    java.util.zip.GZIPInputStream.))

;; STEP: DOWNLOAD
(defmulti download-service!
  "Retrieves pricing data (and relevant resources) for a given service.
   dir is the actual directory (as a File) where to save these data, this directory is unique to this download.
   clients is a map of clients."
  (fn [qualified-service {:keys [provider]} dir clients] provider))

(defonce
  ^{:doc "Atom, contains the config-map returned by the most recent call to download-services,
   Intented as a REPL helper, NOT MEANT FOR NON-INTERACTIVE USAGE."}
  *last-download
  (atom nil))

(defn download-services
  "Takes a collection of services (as :provider/service keywords) and downloads their pricing data.
   Returns a service config-map, that is a map of qualified services (as keywords) to tags (as strings)."
  [download-path qualified-services clients]
  (reset! *last-download
    (into {}
      (for [qualified-service qualified-services
            :let [provider (namespace qualified-service)
                  service (name qualified-service)
                  timestamp (-> java.time.ZoneOffset/UTC
                              .toString
                              java.time.ZoneId/of
                              java.time.ZonedDateTime/now
                              (.format (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd-HHmmss")))
                  dir (ensure-directory! download-path qualified-service timestamp "in")]]
        [qualified-service
         (try
           (download-service! qualified-service (services qualified-service) dir clients)
           timestamp
           (catch Exception ex
             (ak/unavailable (str "Download of " (name qualified-service) " failed.") {} ex)))]))))

;; STEP: ITERATE
(defmulti do-transform-pricing
  "Performs the actual trasformation, passes (one by one) tarnsformed entity-maps or tarnsactions to tx1!.
   Dispatches on provider."
  (fn [download-path service+tag {:keys [provider]} tx1!] provider))

(defn transform-pricing
  "Transform raw json data from the specified service into datomic entity maps.
   and pass each resulting entity map to the reducing function rf with init being its accumulator initial value.
   rf MUST have a completing arity (1-arg).
   Returns the final value of the accumulator."
  [download-path [qualified-service :as service+tag] rf init]
  (let [info (services qualified-service)
        acc (atom init)
        reduced! (Throwable.)
        tx1! (fn [tx] (when (reduced? (swap! acc rf tx)) (throw reduced!)))]
    (try
      (do-transform-pricing download-path service+tag info tx1!)
      (catch Throwable t
        (when-not (identical? reduced! t) (throw t))
        (swap! unreduced acc)))
    (swap! acc rf)))

(defn dryrun-service
  "Attempts to transform data from one (already downloaded) service.
     The service is specified through a services map -- or is the most recently downloaded service in the repl session.
     By default (and on success) it returns the number of produced entity maps.
     You can also use this function as a transducing context which receives entity maps."
  ([download-path] (dryrun-service @*last-download download-path))
  ([single-service-config-map download-path] (dryrun-service (map (constantly 1)) + 0 single-service-config-map download-path))
  ([rf single-service-config-map download-path] (dryrun-service rf (rf) single-service-config-map download-path))
  ([rf init single-service-config-map download-path]
   (when-not (= 1 (count single-service-config-map))
     (throw (ex-info (str "dryrun-service can only transform one service at a time, got " (count single-service-config-map) ".")
              {:config-map single-service-config-map})))
   (let [[qualified-service :as service+tag] (first single-service-config-map)]
     (transform-pricing download-path service+tag rf init)))
  ([xform rf init single-service-config-map download-path]
   (dryrun-service (xform rf) init single-service-config-map download-path)))

(defn- prn-to-writer [^java.io.Writer w x]
  (binding [*out* w] (prn x))
  w)

(defn transform-services
  "Transforms data from specified services and produce one .edn.gz for each service."
  [download-path services-config-map]
  (doall
    (pool/upmap (pool/ncpus)
      (fn [[qualified-service tag :as service+tag]]
        (with-open [out (java.io.OutputStreamWriter.
                          (open-gz-file-out
                            (java.io.File. (ensure-directory! download-path qualified-service tag "out")
                              (str (name qualified-service) ".edn.gz")))
                          "UTF-8")]
          (transform-pricing download-path service+tag (completing prn-to-writer) out)))
      services-config-map))
  nil)

;; STEP: IMPORT

(defn term-progress [c]
  (let [tick (async/chan (async/dropping-buffer 1))]
    (async/go
      (while true
        (async/<! (async/timeout 200))
        (async/>! tick true)))
    (async/go-loop [m (doto {} prn)]
      (let [[x port] (async/alts! [c tick])]
        (if (and x (identical? port c))
          (recur (assoc m x (inc (m x 0))))
          (do
            (print "\u001B[A\u001B[K")
            (prn m)
            (flush)
            (if x (recur m))))))))

(defmacro with-progress [progress-chan & body]
  `(let [~progress-chan (async/chan 10)
         p# (term-progress ~progress-chan)]
     (try
       ~@body
       (finally
         (async/close! ~progress-chan)
         (async/<!! p#)))))

(defn- mass-transact
  [conn txes-in {:keys [max-in-flight factor progress]
                 :or   {max-in-flight 50
                        factor        2}}]
  (let [progress (or progress (async/chan (async/dropping-buffer 1)))]
    (async/go-loop [open true
                in-flight {}]
      (when (or open (seq in-flight))
        (let [[val port]
              (async/alts!
                (cond-> (vec (keys in-flight))
                  (and open (< (count in-flight) max-in-flight)) (conj txes-in)))]
          (when (and open (< (count in-flight) max-in-flight))
            (async/>! progress :full))
          (cond
            (identical? port txes-in)
            (if val
              (recur open (assoc in-flight (da/transact conn {:tx-data val}) val))
              (recur false in-flight))

            (:tx-data val)
            (do (async/>! progress :tx) (recur open (dissoc in-flight port)))

            (ak/retryable? val)
            (let [cat (ak/category val)]
              (async/>! progress cat)
              (or
                ; wait for all in-flight txes to succeed
                ; returns nil or ano
                (loop [pause 1000
                       failed [(in-flight port)]
                       in-flight (dissoc in-flight port)]
                  (cond
                    (seq in-flight)
                    (let [[val port] (async/alts! (vec (keys in-flight)))]
                      (cond
                        (:tx-data val) (do (async/>! progress :tx+) (recur pause failed (dissoc in-flight port)))
                        (ak/retryable? val)
                        (recur pause (conj failed (in-flight port)) (dissoc in-flight port))
                        :else val))
                    (seq failed)
                    (do
                      (println "Waiting" (* pause 1e-3) "s\n")
                      (async/<! (async/timeout pause))
                      (async/>! progress cat)
                      (recur (* pause factor) []
                        (into {} (map (fn [tx] [(da/transact conn {:tx-data tx}) tx])) failed)))
                    :else nil))
                (recur open {})))
            :else
            (do
              (println "HELP" (pr-str val)) (newline)
              val)))))))

(defn make-gcp-clients
  []
  {:gcp/client (delay (api/client {:api "cloudbilling" :version "v1"}))})

(defn make-azure-clients
  [{:azure/keys [tenant-id client-id client-secret sub-id]}]
  (let [token (az/auth tenant-id client-id client-secret)]
    {:azure/commerce-client
     (->>
       token
       (az/client :commerce "2015-06-01-preview" sub-id)
       delay)
     :azure/compute-skus-client
     (->>
       token
       (az/client :compute-skus "2019-04-01" sub-id)
       delay)}))

(defn make-clients
  [{:keys [azure gcp]}]
  (cond-> {}
    azure
    (merge (make-azure-clients azure))
    gcp
    (merge (make-gcp-clients))))

(defn throttling-pipe [from to items-per-minute]
  (async/go
    (loop [start (System/currentTimeMillis)
           remaining items-per-minute]
      (if (pos? remaining)
        (when-some [x (async/<! from)]
          (when (async/>! to x)
            (recur start (dec remaining))))
        (let [now (System/currentTimeMillis)
              rem-ms (- 60000 (- now start))]
          (if (pos? rem-ms)
            (do
              (async/<! (async/timeout rem-ms))
              (recur (+ now rem-ms) items-per-minute))
            (recur now items-per-minute)))))
    (async/close! from)
    (async/close! to)))

(defn import-to-datomic
  "Takes a services config map of transformed services and transact transformed entities into Datomic."
  [download-path services-config-map {:keys [conn batch-size entities-per-minute] :or {batch-size 100} :as opts}]
  (when-not (zero? (mod (or entities-per-minute 0) batch-size))
    (binding [*out* *err*]
      (println "It's recommended to pick an entity rate which is a multiple of batch-size.")))
  (let [txes-in
        (async/chan 1 (partition-all batch-size))
        txes-out (if entities-per-minute
                   (let [in (async/chan)]
                     (throttling-pipe in txes-in entities-per-minute)
                     in)
                   txes-in)
        done (if conn
               (mass-transact conn txes-in opts)
               ; fake mass-transact that just prints some data
               (async/go-loop [] (when-some [batches (async/<! txes-in)] (prn (count batches) (first batches)) (recur))))
        tx1! #(async/alt!!
                done ([v] (some->> v (ex-info "Transaction anomaly" {:anomaly v}) throw))
                [[txes-out %]] :ok)]
    (doseq [[qualified-service tag :as service+tag] services-config-map]
      (with-open [in (-> (java.io.File. (ensure-directory! download-path qualified-service tag "out")
                           (str (name qualified-service) ".edn.gz"))
                       open-gz-file-in (java.io.InputStreamReader. "UTF-8") java.io.PushbackReader.)]
        (loop []
          (when-some [tx-data (edn/read {:eof nil} in)]
            (tx1! tx-data)
            (recur)))))
    (tx1! {:db/ident ::services-config
           :db/doc   (pr-str services-config-map)})
    (async/close! txes-out)
    (some->> (async/<!! done) (ex-info "Transaction anomaly") throw)))

(defn existing-services-config
  "Returns the services config map that was used to create the existing pricing db.
   It can then be used to create an updated services config map tor recreating the db."
  [db]
  (:db/doc (d/pull db '[(:db/doc :xform clojure.edn/read-string)] ::services-config)))

;; PROVIDERS SPECIFICS:
;; AZURE
(defn- download-azure-meters [{:azure/keys [commerce-client compute-skus-client]} category dir]
  (let [r (ak/?! (az/invoke (force commerce-client)
                   {:op      :RateCard_Get
                    :request {:$filter "OfferDurableId eq 'MS-AZR-0003P' and Currency eq 'USD' and Locale eq 'us-US' and RegionInfo eq 'US'"}}))]
    (dump-to-json-gz-file (:body r) dir "RateCard_Get")
    (when (= category "Virtual Machines")
      (let [r (ak/?! (az/invoke (force compute-skus-client) {:op :ResourceSkus_List :request {}}))]
        (dump-to-json-gz-file (:body r) dir "ResourcesSku_List_Get")))))

(defmethod download-service! :azure [qualified-service {:keys [service-name]} dir clients]
  (download-azure-meters clients service-name dir))

(defn azure-vms-specs [dir]
  (with-open [r (open-gz-file-in (java.io.File. dir "ResourcesSku_List_Get.json.gz"))]
    (let [values (-> r json/read-any (get "value"))]
      (into {}
        (keep
          (fn [{:strs [resourceType capabilities size]}]
            (when (= resourceType "virtualMachines")
              (let [{:keys [ram vcpus vcpus-usable]}
                    (into {}
                      (keep
                        (fn [{:strs [name value]}]
                          (case name
                            "MemoryGB"
                            [:ram (Double/parseDouble value)]
                            "vCPUs"
                            [:vcpus (Double/parseDouble value)]
                            "vCPUsAvailable"
                            [:vcpus-usable (Double/parseDouble value)]
                            nil)))
                      capabilities)
                    vcpus (or vcpus-usable vcpus)]
                [(str/replace size "_" " ") #:cs.azure.vm{:ram ram :vcpus vcpus}]))))
        values))))

(defmethod do-transform-pricing :azure [download-path [qualified-service tag] {:keys [service-name meter-fn aux sub-categories]} tx1!]
  (let [dir (ensure-directory! download-path qualified-service tag "in")]
    (with-open [input (open-gz-file-in (java.io.File. dir "RateCard_Get.json.gz"))]
      (let [input (cond-> {:category service-name
                           :input    input}
                    (= service-name "Virtual Machines") (assoc :vms-specs (azure-vms-specs dir)))
            auxargs (map input aux)]
        (json/read-azure-services (:input input)
          (fn [n meter]
            (if (and (= (:category input) (get meter "MeterCategory"))
                  (or (nil? sub-categories) (contains? sub-categories (get meter "MeterSubCategory"))))
              (do
                (tx1! (apply meter-fn meter auxargs))
                (inc n))
              n)) 0)))))

;; GCP
(defn- download-gcp-pages [*client {:keys [op] :as op-map} dir]
  (let [client (force *client)]
    (loop [page (gcp-result client op-map) page-nb 1]
      (let [file (dump-to-json-gz-file (input page) dir (str op "-" page-nb))
            token (with-open [in (open-gz-file-in file)]
                    (json/scan-gcp-token in))]
        (when (and token (not= "" token))
          (recur (next-page page token) (inc page-nb)))))))

(defn gcp-files-pages-result
  "Creates a paginated result from downloaded responses."
  ([dir op] (gcp-files-pages-result dir op 1))
  ([dir op n]
   (reify GCPPagedResult
     (input [_] (->> (str op "-" n ".json.gz") (java.io.File. dir) open-gz-file-in))
     (next-page [_ token] (gcp-files-pages-result dir op (inc n))))))

(defn- gcp-files-service-name [dir display-name]
  (loop [page (gcp-files-pages-result dir "cloudbilling.services.list")]
    (let [{:keys [service-name token]}
          (with-open [in (input page)]
            (json/read-gcp-services
              in (fn [acc service]
                   (cond-> acc
                     (= (service "displayName") display-name)
                     (assoc :service-name (service "name"))))
              {}
              (fn [acc token]
                (assoc acc :token token))))]
      (or service-name
        (when (seq token) (some-> (next-page page token) recur))))))

(defn- download-gcp-skus [{:gcp/keys [client]} display-name dir]
  (download-gcp-pages client {:op "cloudbilling.services.list"} dir)
  (when-some [parent (gcp-files-service-name dir display-name)]
    (download-gcp-pages client {:op      "cloudbilling.services.skus.list"
                                :request {:parent parent}} dir)))

(defmethod download-service! :gcp [qualified-service {:keys [display-name]} dir clients]
  (download-gcp-skus clients display-name dir))

(defmethod do-transform-pricing :gcp [download-path [qualified-service tag] {:keys [sku-fn]} tx1!]
  (doseq [^java.io.File f (.listFiles (ensure-directory! download-path qualified-service tag "in"))
          :when (not= (.getName f) "cloudbilling.services.list-1.json.gz")]
    (with-open [input (open-gz-file-in f)]
      (json/read-gcp-pricing input nil #(tx1! (sku-fn %2))))))

;; AWS

(defn- download-aws-skus [pricing-url service-name dir]
  (with-open [in (.openStream (java.net.URL. pricing-url))]
    (dump-to-json-gz-file in dir service-name)))

(defmethod download-service! :aws [qualified-service {:keys [url]} dir _]
  (download-aws-skus url (name qualified-service) dir))

(defmethod do-transform-pricing :aws [download-path [qualified-service tag] {:keys [product-fn price-fn provider]} tx1!]
  (with-open [input (open-gz-file-in
                      (java.io.File. (ensure-directory! download-path qualified-service tag "in")
                        (str (name qualified-service) ".json.gz")))]
    (let [{:keys [meta]} (json/read-aws-pricing input nil
                           (fn [_ sku product]
                             (let [product-map (product-fn sku product)]
                               (tx1! product-map)))
                           (fn [_ term sku offer price]
                             (let [price-map (price-fn term sku offer price)]
                               (tx1! price-map))))
          meta' (h/conform! :aws.v1/meta meta {:service qualified-service})]
      (tx1! meta'))))

;; AWS Savings Plan

(defn- download-aws-savings-plan [index-url base-url dir]
  (let [file (dump-to-json-gz-file (.openStream (java.net.URL. index-url)) dir "savings-plan-index")]
    (with-open [input (open-gz-file-in file)]
      (doseq [path (json/read-aws-savings-plan-index input)
              :let [input (java.net.URL. (str base-url path))]]
        (if-some [[_ region] (re-matches #"/savingsPlan/v1\.0/aws/AWSComputeSavingsPlan/\d{14}/([^/]+)/index.json" path)]
          (dump-to-json-gz-file (.openStream input) dir (str "savings-plan." region))
          (throw (ex-info (str "Unexpected SavingsPlan path: " path) {:path path})))))))

(defmethod download-service! :aws-savings-plan [qualified-service {:keys [base-url url]} dir _]
  (download-aws-savings-plan url base-url dir))

(defmethod do-transform-pricing :aws-savings-plan [download-path [qualified-service tag] {:keys [product-fn prices-fn]} tx1!]
  (doseq [^java.io.File f (.listFiles (ensure-directory! download-path qualified-service tag "in"))
          :when (not= (.getName f) "savings-plan-index.json.gz")]
    (with-open [input (open-gz-file-in f)]
      (let [{:keys [meta]}
            (json/read-aws-savings-plan-pricing input nil
              (fn [_ product]
                (let [product-map (product-fn product)]
                  (tx1! product-map)))
              (fn [_ term prices]
                (let [prices-seq (prices-fn term prices)]
                  (doseq [prices-map prices-seq]
                    (tx1! prices-map)))))
            meta' (s/conform :aws.v1.savings-plan/meta meta)]
        (when (= ::s/invalid meta')
          (throw (ex-info "Invalid AWS Savings Plan metadata"
                   {:meta         meta
                    :explain-data (s/explain-data :aws.v1.savings-plan/meta meta)})))
        (tx1! meta')))))

(def prod-services
  {:aws/app-stream            "20210526-220336"
   :aws/dynamodb              "20220126-221813"
   :aws/ec2                   "20220726-191336"
   :aws/ecs-fargate           "20210526-220337"
   :aws/rds                   "20220726-191317"
   :aws/savings-plan          "20220726-191937"
   :aws/workspaces            "20210526-220622"
   :azure/managed-disks       "20210526-220527"
   :azure/sql-database        "20210608-224403"
   :azure/virtual-machines    "20210526-220552"
   :gcp/big-query             "20210526-220809"
   :gcp/big-query-reservation "20210526-220618"
   :gcp/big-query-storage     "20210526-220334"
   :gcp/ce-disk-image         "20220419-203241"
   :gcp/cloud-sql             "20210526-220525"})

;; Download
(comment
  (def download-path "pricing")
  (def clients (make-clients {:azure (read-string (slurp (io/resource "azure-creds.edn")))
                              :gcp   {}}))

  (list-downloaded-services download-path)

  ;; download new pricing data for all services (prefer single service method)
  (download-services download-path (set (keys services)) clients)
  ;; download a single cloud provider
  (download-services download-path (set (filter #(= "azure" (namespace %)) (keys services))) clients)

  ;; Download for a single service (usual method)
  (download-services download-path #{:aws/rds} nil)
  (download-services download-path #{:aws/ec2 :aws/savings-plan :aws/rds} nil)
  (download-services download-path #{:aws/savings-plan} nil)
  (download-services download-path #{:gcp/ce-disk-image} clients)
  (download-services download-path #{:azure/virtual-machines} clients)

  (download-services download-path #{:aws/rds} nil)
  (download-services download-path #{:aws/ec2} nil)
  (download-services download-path #{:aws/dynamodb} nil))

;; Dryrun (validate import)
(comment
  ;; count by default
  (dryrun-service download-path)
  (ex-data *e)

  ;; returns 0.5% of the full batch from services
  (dryrun-service (random-sample 0.005) conj [] #:gcp{:ce-disk-image "20211029-202750"} download-path)

  ;; filter on location
  (dryrun-service (filter #(= (:aws.v1.workspaces.product/location %) "us-east-1")) conj [] (deref *last-download) download-path)

  ;; dryrun all
  (def download-path "/tmp")
  (dryrun-service {:aws/ec2 "20211214-131617"} download-path)
  (dryrun-service {:aws/dynamodb "20220126-221813"} download-path)
  (dryrun-service {:aws/ec2 "20220726-191336"} download-path)
  (dryrun-service {:aws/rds "20220726-191317"} download-path)
  (dryrun-service {:aws/savings-plan "20220726-191937"} download-path)
  (ex-data *e)

  (require '[clojure.pprint :as pp])
  (pp/pprint (ex-data *e))

  ;; dryrun one cloud provider
  (dryrun-service (set (filter #(= "azure" (namespace %)) (keys services))) download-path)
  (ex-data *e))

;; Transform: Create transformed service edn.gz files
(comment
  (try
    (transform-services download-path (deref *last-download))
    (catch Exception e
      (clojure.pprint/pprint (ex-data e))))

  ;; Transform all
  (transform-services download-path (select-keys prod-services [:aws/savings-plan :aws/rds :aws/ec2]))
  (transform-services download-path (select-keys prod-services [:aws/rds]))
  (transform-services download-path prod-services))

;; Check if you can find your new instance type
(comment
  (def j-edn
    (with-open [rdr (-> download-path
                      (io/file "aws" "ec2" "20220221-215208" "out" "ec2.edn.gz")
                      #_(io/file ".." "pricing-api" download-path "azure" "virtual-machines" "20210526-220552" "out" "virtual-machines.edn.gz")
                      io/input-stream
                      java.util.zip.GZIPInputStream.
                      io/reader
                      java.io.PushbackReader.)]
      (loop [vs []]
        (let [v (edn/read {:eof rdr}
                  rdr)]
          (if (identical? rdr v)
            vs
            (recur (conj vs v)))))))
  (def instance-types (into (sorted-set)
                        (map :aws.v1.ec2/instance-type)
                        j-edn))
  (keep #{"dl1" "c6i"} instance-types))

;; Import
(comment
  (def client (d/client {:server-type :dev-local :storage-dir :mem :system "csp-pricing"}))
  (def client (d/client {:server-type :dev-local :system "csp-pricing"}))

  (d/list-databases client {})

  (def db-name "pricelist")

  (d/create-database client {:db-name db-name})
  (def conn (d/connect client {:db-name db-name}))
  (transact-schema conn)
  ;; END DB setup

  (transform-services download-path (deref *last-download))
  (import-to-datomic download-path (deref *last-download)
    {:conn       conn
     :batch-size 100
     #_#_:entities-per-minute 20000})


  ;; with conn = nil ->  prints the first of each batch simulating a datomic transact
  (import-to-datomic download-path prod-services
    {:conn       nil
     :batch-size 100
     #_#_:entities-per-minute 20000})

  ;; Transact to datomic
  ;; Long operation. Expect for
  ;; - dev-local on-disk: ~2h
  ;; - remote with t3.2xlarge: ~30min
  (import-to-datomic download-path prod-services
    {:conn       conn
     :batch-size 200
     #_#_:entities-per-minute 20000})
  (existing-services-config (d/db conn))

  (import-to-datomic download-path (select-keys prod-services [:aws/rds])
    {:conn       conn
     :batch-size 200
     #_#_:entities-per-minute 20000}))

(comment

  (def download-path "/tmp")

  (download-services download-path #{:azure.virtual-machines/licenses} clients)

  #:azure{:sql-database "20210526-131525"}

  (dryrun-service (random-sample 0.005) conj [] #:azure.virtual-machines{:licenses "20210527-061439"} download-path)
  (transform-services download-path #:azure{:sql-database "20210526-131525"})

  #:azure{:sql-database "20210526-131525"}

  (dryrun-service (random-sample 0.005) conj [] #:azure.virtual-machines{:licenses "20210527-061439"} download-path)
  (transform-services download-path #:azure{:sql-database "20210526-131525"})

  (import-to-datomic download-path {:azure/managed-disks "20210517-145908"}
    {:conn                conn
     :batch-size          1000
     :entities-per-minute 20000}))

(comment


  (def j (jjj/parse-stream (io/reader (ensure-directory! download-path :azure/managed-disks "20210525-073348" "in/skus.json"))))

  (first (nnext j))

  (def basic (get (into {} (map #(vector (% "name") (% "supportedServiceLevelObjectives"))) j) "BusinessCritical"))

  (into #{} (map #(% "name")) j)

  #{"GeneralPurpose" "Free" "Premium" "Standard"
    "BusinessCritical" "Basic" "System"
    "Stretch" "Hyperscale" "DataWarehouse"})


