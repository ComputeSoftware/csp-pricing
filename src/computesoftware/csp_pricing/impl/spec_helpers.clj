(ns computesoftware.csp-pricing.impl.spec-helpers
  (:require
    [clojure.spec.alpha :as s]))

(defn- check-mandatory-fields [field-to-kw+spec]
  (let [mandatory-fields
        (into #{}
          (keep (fn [[field [kw spec & opt]]]
                  (when-not (seq opt) kw)))
          field-to-kw+spec)]
    (s/conformer
      (fn [m]
        (if (seq (reduce disj mandatory-fields (keys m)))
          ::s/invalid
          (into {} (keep (fn [[k [_ v]]]
                           (when-not (= (namespace k) "discard.me")
                             [k v]))) m))))))

(defn- spec-form [field-to-kw+spec]
  (s/and
    (let [arg (gensym "arg")]
      ((eval                                                ; spec, look at what I must do!
         `(fn [~arg]
            (s/coll-of
              (s/or
                ~@(mapcat
                    (fn [[field [kw spec & opt]]]
                      (let [spec
                            (cond
                              (map? spec)
                              `(s/conformer (fn [x#] (get ~spec x# ::s/invalid)))
                              (instance? java.util.regex.Pattern spec)
                              `(fn [x#] (and (string? x#) (boolean (re-matches ~spec x#))))
                              :else `(second (~arg ~field)))]
                        [(or kw (keyword (gensym (symbol "discard.me" field))))
                         `(s/tuple #{~field} ~spec)]))
                    field-to-kw+spec)))))
       field-to-kw+spec))
    (check-mandatory-fields field-to-kw+spec)))

(defn strictly-conform-json-object [field-to-kw+spec]
  (spec-form field-to-kw+spec)
  #_(s/conformer
      (fn [json-object]
        (reduce-kv
          (fn [m field [kw spec & opt]]
            (if (and (seq opt) (nil? (json-object field)))
              m
              (let [spec (cond
                           (map? spec) (s/conformer #(get spec % ::s/invalid))
                           (instance? java.util.regex.Pattern spec) #(boolean (re-matches spec %))
                           :else spec)
                    v (s/conform spec (get json-object field))]
                (cond
                  (= ::s/invalid v) (reduced ::s/invalid)
                  kw (assoc m kw v)
                  :else m))))
          {} field-to-kw+spec))))

(defn select-keys-conformer
  [ks]
  (s/conformer (fn [m] (select-keys m ks))))

(defn- spec-form2
  [multi-name field-to-kw+spec]
  (let [ms (map
             (fn [[field [kw spec & opt]]]
               (let [;; we may want to pass in a symbol referring to a map. We must eval to get the map.
                     spec (if (symbol? spec) (eval spec) spec)
                     v-spec (cond
                              (map? spec)
                              `(s/conformer (fn [~'k] (get ~spec ~'k ::s/invalid)))
                              (instance? java.util.regex.Pattern spec)
                              `(fn [~'s] (and (string? ~'s) (boolean (re-matches ~spec ~'s))))
                              :else spec)]
                 `(defmethod ~multi-name ~field
                    [~'_]
                    (s/and (s/tuple #{~field} ~v-spec)
                      (s/conformer (fn [[~'_ v#]] (when ~kw [~kw v#])))))))
             field-to-kw+spec)
        {:keys [ks req]} (reduce-kv
                           (fn [acc _ [kw spec & opt]]
                             (cond-> acc
                               kw (update :ks (fnil conj #{}) kw)
                               (and kw (not (seq opt))) (update :req (fnil conj #{}) kw)))
                           {} field-to-kw+spec)]
    `(do
       (defmulti ~multi-name first)
       ~@ms
       (s/and
         (s/coll-of (s/multi-spec ~multi-name (fn [x# ~'_] x#)) :kind map? :conform-keys true)
         (select-keys-conformer ~ks)
         (s/keys :req ~req)))))

(defmacro strictly-conform-json-object2
  [multi-name field-to-kw+spec]
  (let [x (if (symbol? field-to-kw+spec) @(resolve field-to-kw+spec) field-to-kw+spec)]
    (spec-form2 multi-name x)))

(comment
  (s/def ::foo
    (strictly-conform-json-object
      {"servicecode" [:aws.v1.product/service-code #{"AmazonEC2"}]}))

  (s/def ::foo2
    (strictly-conform-json-object2
      foo-spec
      {"servicecode"  [:aws.v1.product/service-code #{"AmazonEC2"}]
       "servicecode2" [nil #{"AmazonEC2"}]}))
  (s/explain-data ::foo2 {"servicecode"  "AmazonEC2"
                          "servicecode2" "AmazonEC2"}))


(s/def ::double
  (s/conformer (fn [str]
                 (try
                   (Double/parseDouble str)
                   (catch java.lang.NumberFormatException _ ::s/invalid)))))

(defn conform!
  [spec x extra-context-on-failure]
  (let [c (s/conform spec x)]
    (if (s/invalid? c)
      (throw (ex-info (str "Invalid conformed entity " (pr-str spec) ".")
               (merge
                 extra-context-on-failure
                 {:value        x
                  :spec         spec
                  :explain-data (s/explain-data spec x)})))
      c)))
