(ns computesoftware.csp-pricing.azure.common
  (:require
    [clojure.spec.alpha :as s]))

(def schema
  [#:db{:ident       :azure.meter/effective-date,
        :valueType   :db.type/instant,
        :cardinality :db.cardinality/one}

   #:db{:ident       :azure.meter/included-quantity,
        :valueType   :db.type/double,
        :cardinality :db.cardinality/one}

   #:db{:ident       :azure.meter/category,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #:db{:ident       :azure.meter/id,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one
        :db/unique   :db.unique/identity}

   #:db{:ident       :azure.meter/name,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #:db{:ident       :azure.meter/rates,
        :valueType   :db.type/ref,
        :cardinality :db.cardinality/many
        :isComponent true}

   #:db{:ident       :azure.meter.rate/threshold,
        :valueType   :db.type/double,
        :cardinality :db.cardinality/one}

   #:db{:ident       :azure.meter.rate/rate,
        :valueType   :db.type/double,
        :cardinality :db.cardinality/one}

   #:db{:ident       :azure.meter/region,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #:db{:ident       :azure.region/name,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one,
        :db/unique   :db.unique/identity}

   #:db{:ident       :azure.meter/sub-category,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #:db{:ident       :azure.meter/tags,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/many}

   #:db{:ident       :azure.meter/unit,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}])

(defn timestamp [s]
  (try
    (clojure.instant/read-instant-timestamp s)
    (catch Exception e
      ::s/invalid)))

(s/def :azure.meter/effective-date any? #_(fn [x] (instance? java.util.Date x)))
(s/def :azure.meter.impl/effective-date (s/conformer timestamp))

(s/def :azure.meter/included-quantity (s/and number? (s/conformer double)))

(s/def :azure.meter/category string?)

(s/def :azure.meter/id string?)

(s/def :azure.meter/name string?)

(def number-as-string
  (s/conformer
    (fn [s]
      (let [n (try (clojure.edn/read-string s) (catch Exception e nil))]
        (if (number? n)
          n
          ::s/invalid)))
    pr-str))

(s/def :azure.meter.impl/rates
  (s/and
    (s/map-of number-as-string number? :conform-keys true)
    (s/conformer
      (fn [m]
        (into []
          (map (fn [[k v]]
                 #:azure.meter.rate
                         {:threshold (double k)
                          :rate      (double v)}))
          m)))))
(s/def :azure.meter.rate/threshold double?)
(s/def :azure.meter.rate/rate double?)
(s/def :azure.meter/rates
  (s/coll-of (s/keys :req [:azure.meter.rate/threshold :azure.meter.rate/rate])))

(s/def :azure.meter/region string?)

(s/def :azure.meter/sub-category string?)

(s/def :azure.meter/tags (s/coll-of string?))

(s/def :azure.meter/unit string?)

(def baseline
  {"EffectiveDate"    [:azure.meter/effective-date :azure.meter.impl/effective-date]
   "IncludedQuantity" [:azure.meter/included-quantity :azure.meter/included-quantity]
   "MeterCategory"    [:azure.meter/category :azure.meter/category]
   "MeterId"          [:azure.meter/id :azure.meter/id]
   "MeterName"        [:azure.meter/name :azure.meter/name]
   "MeterRates"       [:azure.meter/rates :azure.meter.impl/rates]
   "MeterRegion"      [:azure.meter/region :azure.meter/region]
   "MeterSubCategory" [:azure.meter/sub-category :azure.meter/sub-category]
   "MeterTags"        [:azure.meter/tags :azure.meter/tags]
   "Unit"             [:azure.meter/unit :azure.meter/unit]})
