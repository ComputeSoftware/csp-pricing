(ns computesoftware.csp-pricing.azure.managed-disks
  (:require
    [clojure.spec.alpha :as s]
    [computesoftware.csp-pricing.azure.common :as common]
    [computesoftware.csp-pricing.impl.spec-helpers :as h]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [clojure.string :as str]))

(def schema
  [#:db{:ident       :cs.azure.md/disk-tier,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}])

(def meter-spec-map
  (assoc common/baseline
    "MeterCategory" [:azure.meter/category #{"Storage"}]))

(s/def :azure.md/meter
  (h/strictly-conform-json-object2
    meter-spec meter-spec-map))

(defn assoc-synthetic-fields [meter]
  (let [[_ cs-disk] (re-matches #"([ESP]\d+) Disks" (:azure.meter/name meter))]
    (cond-> meter cs-disk (assoc :cs.azure.md/disk-tier cs-disk))))

(defn do-meter
  [meter]
  (let [meter-c (h/conform! :azure.md/meter meter {})]
    (assoc-synthetic-fields meter-c)))
