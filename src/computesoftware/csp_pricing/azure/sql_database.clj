(ns computesoftware.csp-pricing.azure.sql-database
  (:require
    [clojure.spec.alpha :as s]
    [computesoftware.csp-pricing.azure.common :as common]
    [computesoftware.csp-pricing.impl.spec-helpers :as h]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [clojure.string :as str]))

(def schema
  [#:db{:ident       :cs.azure.sql-db/tier,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}
   #:db{:ident       :cs.azure.sql-db/component,
        :valueType   :db.type/keyword,
        :cardinality :db.cardinality/one}
   #:db{:ident       :cs.azure.sql-db/compute-model,
        :valueType   :db.type/keyword,
        :cardinality :db.cardinality/one}
   #:db{:ident       :cs.azure.sql-db/family,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}
   #:db{:ident       :cs.azure.sql-db/type,
        :valueType   :db.type/keyword,
        :cardinality :db.cardinality/many}])

(def meter-spec-map
  (assoc common/baseline
    "MeterCategory" [:azure.meter/category #{"SQL Database"}]))

(s/def :azure.sql/meter
  (h/strictly-conform-json-object2
    meter-spec meter-spec-map))

(defn assoc-synthetic-fields [{:azure.meter/keys [sub-category name] :as meter}]
  (merge meter
    (when-some [[_ component series] (re-find #"- (Storage|SQL License|Compute (.+?)(?:[- ]Series)?)$" sub-category)]
      (cond->
        {:cs.azure.sql-db/component (case component
                                      "Storage" :storage
                                      "SQL License" :sql-license
                                      :compute)}
        series
        (assoc :cs.azure.sql-db/family (case series
                                         "FSv2" "Fsv2"
                                         ("M" "Gen4" "Gen5" "DC" "Latest Gen") series))))
    (when-some [[_ tier] (re-find #"^Single/Elastic Pool (Business Critical|General Purpose) " sub-category)]
      #:cs.azure.sql-db
              {:tier
               (case tier
                 "Business Critical" "BusinessCritical"
                 "General Purpose" "GeneralPurpose")
               :type [:single-database :elastic-pool]})
    (when-some [[_ component series] (re-find #"^SingleDB Hyperscale " sub-category)]
      #:cs.azure.sql-db
              {:tier "Hyperscale"
               :type :single-database})
    (when-some [[_ component series] (re-find #"^Single General Purpose - Serverless " sub-category)]
      #:cs.azure.sql-db
              {:tier          "GeneralPurpose"
               :compute-model :serverless
               :type          :single-database})
    (when-some [[_ type tier] (re-matches #"^(Single|Elastic Pool) (?:- )?(.+)" sub-category)]
      #:cs.azure.sql-db
              {:tier tier
               :type (case type "Single" :single-database :elastic-pool)})))

(defn do-meter
  [meter]
  (let [meter-c (h/conform! :azure.sql/meter meter {})]
    (assoc-synthetic-fields meter-c)))

(comment

  '[["Single/Elastic Pool General Purpose - Compute Gen4" "1 Hour"]
    ["Single/Elastic Pool Business Critical - Compute Gen5" "1 Hour"]
    ["Elastic Pool - Premium RS" "1/Day"]
    ["Single Premium RS" "1/Day"]
    ["Single/Elastic Pool Business Critical - Compute M Series"
     "1 Hour"]
    ["Single/Elastic Pool Business Critical - Storage" "1M"]
    ["Single/Elastic Pool Business Critical - SQL License" "1 Hour"]
    ["Single General Purpose - Serverless - Compute DC-Series" "1 Hour"]
    ["Single/Elastic Pool General Purpose - SQL License" "1 Hour"]
    ["Single Basic" "1/Day"]
    ["Compute Reservation" "1 Hour"]
    ["SingleDB Hyperscale - Storage" "1 GB/Month"]
    ["SingleDB Hyperscale - Compute Gen5" "1 Hour"]
    ["Single/Elastic Pool Business Critical - Storage" "1 GB/Month"]
    ["Standard - Storage" "1 GB/Month"]
    ["Single Premium" "1/Day"]
    ["Premium - Storage" "1 GB/Month"]
    ["Single General Purpose - Serverless - Compute Gen5" "1 Hour"]
    ["Single Free" "1/Day"]
    ["Single/Elastic Pool Business Critical - Compute Gen4" "1 Hour"]
    ["SingleDB Hyperscale - Storage" "1M"]
    ["Single Standard" "1/Day"]
    ["Managed Instance PITR Backup Storage" "1 GB/Month"]
    ["Elastic Pool - Basic" "1/Day"]
    ["Single/Elastic Pool General Purpose - Storage" "1M"]
    ["Single/Elastic Pool General Purpose - Compute Gen5" "1 Hour"]
    ["Elastic Pool - Standard" "1/Day"]
    ["SingleDB Hyperscale - Compute Gen4" "1 Hour"]
    ["SingleDB Hyperscale - SQL License" "1 Hour"]
    ["Single/Elastic Pool Business Critical - Compute DC-Series"
     "1 Hour"]
    ["Single/Elastic Pool General Purpose - Compute FSv2 Series"
     "1 Hour"]
    ["SingleDB Hyperscale - Compute DC-Series" "1 Hour"]
    ["Single General Purpose - Serverless - Compute Gen4" "1 Hour"]
    ["Single/Elastic Pool General Purpose - Storage" "1 GB/Month"]
    ["Single/Elastic Pool General Purpose - Compute DC-Series" "1 Hour"]
    ["Elastic Pool - Premium" "1/Day"]
    ["Single/Elastic Pool PITR Backup Storage" "1 GB/Month"]
    ["LTR Backup Storage" "1 GB/Month"]]

  (def subcats (map first *1))

  (->> subcats
    (remove
      #(re-matches #"Single/Elastic Pool (Business Critical|General Purpose) - (Storage|SQL License|Compute (.+?)(?:[- ]Series)?)" %))
    (remove
      #(re-matches #"SingleDB Hyperscale - (Storage|SQL License|Compute (.+?)(?:[- ]Series)?)" %))
    )

  (for [sc subcats]
    (assoc-synthetic-fields
      {:azure.meter/sub-category sc}))
  )
