(ns computesoftware.csp-pricing.gcp.cloud-sql
  (:require
    [datomic.client.api :as d]
    [clojure.spec.alpha :as s]
    [computesoftware.csp-pricing.gcp.common :as common]
    [computesoftware.csp-pricing.impl.spec-helpers :as h]))

(s/def :cs.gcp.cloud-sql/isHA boolean?)
(s/def :cs.gcp.cloud-sql/db-type string?)
(s/def :cs.gcp.cloud-sql/machine-type #{:cs.gcp.cloud-sql.machine-type/generic
                                        :cs.gcp.cloud-sql.machine-type/highmem
                                        :cs.gcp.cloud-sql.machine-type/standard
                                        :cs.gcp.cloud-sql.machine-type/custom})
(s/def :cs.gcp.cloud-sql.component.storage/disk-type #{:cs.gcp.cloud-sql.component.storage.disk-type/snapshot
                                                       :cs.gcp.cloud-sql.component.storage.disk-type/hdd
                                                       :cs.gcp.cloud-sql.component.storage.disk-type/ssd})
(s/def :cs.gcp.cloud-sql.component.network/type #{:cs.gcp.cloud-sql.component.network.type/egress-google
                                                  :cs.gcp.cloud-sql.component.network.type/egress-inter-zone
                                                  :cs.gcp.cloud-sql.component.network.type/egress-inter-connect
                                                  :cs.gcp.cloud-sql.component.network.type/egress-inter-region
                                                  :cs.gcp.cloud-sql.component.network.type/external-traffic
                                                  :cs.gcp.cloud-sql.component.network.type/egress-internet})
(s/def :cs.gcp.cloud-sql.component.network/from-region string?)
(s/def :cs.gcp.cloud-sql.component.network/to-region string?)
(s/def :cs.gcp.cloud-sql.component.network/region string?)
(s/def :cs.gcp.cloud-sql/machine-family #{:cs.gcp.cloud-sql.machine-family/intel-n1
                                          :cs.gcp.cloud-sql.machine-family/shared})
(s/def :cs.gcp.cloud-sql/component #{:cs.gcp.cloud-sql.component/ip-address
                                     :cs.gcp.cloud-sql.component/storage
                                     :cs.gcp.cloud-sql.component/network
                                     :cs.gcp.cloud-sql.component/cpu
                                     :cs.gcp.cloud-sql.component/ip-address-idling-hour
                                     :cs.gcp.cloud-sql.component/ram})
(s/def :cs.gcp.cloud-sql/shared-core-type #{:cs.gcp.cloud-sql.shared-core-type/f1-micro
                                            :cs.gcp.cloud-sql.shared-core-type/g1-small})
(s/def :cs.gcp.cloud-sql.component.cpu/cpu-number int?)
(s/def :cs.gcp.cloud-sql.component.ram/ram-gb double?)


(s/def :cs.gcp/cloud-sql (s/keys :opt [:cs.gcp.cloud-sql/isHA
                                       :cs.gcp.cloud-sql/db-type
                                       :cs.gcp.cloud-sql/machine-type
                                       :cs.gcp.cloud-sql.component.storage/disk-type
                                       :cs.gcp.cloud-sql.component.network/type
                                       :cs.gcp.cloud-sql.component.network/from-region
                                       :cs.gcp.cloud-sql.component.network/to-region
                                       :cs.gcp.cloud-sql.component.network/region
                                       :cs.gcp.cloud-sql/machine-family
                                       :cs.gcp.cloud-sql/component
                                       :cs.gcp.cloud-sql/shared-core-type
                                       :cs.gcp.cloud-sql.component.cpu/cpu-number
                                       :cs.gcp.cloud-sql.component.ram/ram-gb]))

(def schema [#:db{:ident       :cs.gcp.cloud-sql/isHA,
                  :valueType   :db.type/boolean
                  :cardinality :db.cardinality/one}

             #:db{:ident       :cs.gcp.cloud-sql/db-type,
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :cs.gcp.cloud-sql/machine-type,
                  :valueType   :db.type/ref,
                  :cardinality :db.cardinality/one}
             {:db/ident :cs.gcp.cloud-sql.machine-type/custom}
             {:db/ident :cs.gcp.cloud-sql.machine-type/generic}
             {:db/ident :cs.gcp.cloud-sql.machine-type/standard}
             {:db/ident :cs.gcp.cloud-sql.machine-type/highmem}

             #:db{:ident       :cs.gcp.cloud-sql.component.storage/disk-type
                  :valueType   :db.type/ref,
                  :cardinality :db.cardinality/one}
             {:db/ident :cs.gcp.cloud-sql.component.storage.disk-type/hdd}
             {:db/ident :cs.gcp.cloud-sql.component.storage.disk-type/ssd}
             {:db/ident :cs.gcp.cloud-sql.component.storage.disk-type/snapshot}

             #:db{:ident       :cs.gcp.cloud-sql.component.network/type
                  :valueType   :db.type/ref,
                  :cardinality :db.cardinality/one}
             {:db/ident :cs.gcp.cloud-sql.component.network.type/egress-inter-connect}
             {:db/ident :cs.gcp.cloud-sql.component.network.type/egress-inter-zone}
             {:db/ident :cs.gcp.cloud-sql.component.network.type/external-traffic}
             {:db/ident :cs.gcp.cloud-sql.component.network.type/egress-google}
             {:db/ident :cs.gcp.cloud-sql.component.network.type/egress-internet}
             {:db/ident :cs.gcp.cloud-sql.component.network.type/egress-inter-region}

             #:db{:ident       :cs.gcp.cloud-sql.component.network/from-region
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :cs.gcp.cloud-sql.component.network/to-region
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :cs.gcp.cloud-sql.component.network/region
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :cs.gcp.cloud-sql/machine-family
                  :valueType   :db.type/ref,
                  :cardinality :db.cardinality/one}
             {:db/ident :cs.gcp.cloud-sql.machine-family/intel-n1}
             {:db/ident :cs.gcp.cloud-sql.machine-family/shared}

             #:db{:ident       :cs.gcp.cloud-sql/component,
                  :valueType   :db.type/ref,
                  :cardinality :db.cardinality/one}
             {:db/ident :cs.gcp.cloud-sql.component/cpu}
             {:db/ident :cs.gcp.cloud-sql.component/ram}
             {:db/ident :cs.gcp.cloud-sql.component/storage}
             {:db/ident :cs.gcp.cloud-sql.component/network}
             {:db/ident :cs.gcp.cloud-sql.component/ip-address}
             {:db/ident :cs.gcp.cloud-sql.component/ip-address-idling-hour}

             #:db{:ident       :cs.gcp.cloud-sql/shared-core-type,
                  :valueType   :db.type/ref,
                  :cardinality :db.cardinality/one}
             {:db/ident :cs.gcp.cloud-sql.shared-core-type/f1-micro}
             {:db/ident :cs.gcp.cloud-sql.shared-core-type/g1-small}

             #:db{:ident       :cs.gcp.cloud-sql.component.cpu/cpu-number,
                  :valueType   :db.type/long
                  :cardinality :db.cardinality/one}
             #:db{:ident       :cs.gcp.cloud-sql.component.ram/ram-gb,
                  :valueType   :db.type/double
                  :cardinality :db.cardinality/one}])

(s/def :gcp.cloud-sql.product/name string?)
(s/def :gcp.cloud-sql.product/sku-id string?)
(s/def :gcp.cloud-sql.product/description string?)
(s/def :gcp.cloud-sql.product/service-regions
  #{"asia-east1"
    "asia-east2"
    "asia-northeast1"
    "asia-northeast2"
    "asia-northeast3"
    "asia-south1"
    "asia-south2"
    "asia-southeast1"
    "asia-southeast2"
    "australia-southeast1"
    "australia-southeast2"
    "europe-central2"
    "europe-north1"
    "europe-west1"
    "europe-west2"
    "europe-west3"
    "europe-west4"
    "europe-west6"
    "global"
    "northamerica-northeast1"
    "northamerica-northeast2"
    "southamerica-east1"
    "us-central1"
    "us-east1"
    "us-east4"
    "us-west1"
    "us-west2"
    "us-west3"
    "us-west4"})
(s/def :gcp.cloud-sql.product/service-provider-name #{"Google"})
(s/def :gcp.cloud-sql.product.category/service-display-name #{"Cloud SQL"})
(s/def :gcp.cloud-sql.product.category/resource-family #{"ApplicationServices" "Network"})
(s/def :gcp.cloud-sql.product.category/resource-group #{"ExternalTraffic"
                                                        "InterregionEgress"
                                                        "InterzoneEgress"
                                                        "IpAddress"
                                                        "PDSnapshot"
                                                        "PDStandard"
                                                        "PeeringOrInterconnectEgress"
                                                        "PremiumInternetEgress"
                                                        "SQLGen1Instances" ;; fully decommissioned on March 25th 2020 (see https://cloud.google.com/sql/docs/mysql/deprecation-notice)
                                                        "SQLGen2Instances"
                                                        "SQLGen2InstancesCPU"
                                                        "SQLGen2InstancesF1Micro"
                                                        "SQLGen2InstancesG1Small"
                                                        "SQLGen2InstancesN1Highmem"
                                                        "SQLGen2InstancesN1Standard"
                                                        "SQLGen2InstancesRAM"
                                                        "SSD"
                                                        "Storage"})
(s/def :gcp.cloud-sql.product.category/usage-type #{"OnDemand"})


(s/def :gcp.cloud-sql.product/category
  (h/strictly-conform-json-object2
    category-spec
    {"serviceDisplayName" [:gcp.product.category/service-display-name :gcp.cloud-sql.product.category/service-display-name],
     "resourceFamily"     [:gcp.product.category/resource-family :gcp.cloud-sql.product.category/resource-family],
     "resourceGroup"      [:gcp.product.category/resource-group :gcp.cloud-sql.product.category/resource-group],
     "usageType"          [:gcp.product.category/usage-type :gcp.cloud-sql.product.category/usage-type]}))

(s/def :gcp.cloud-sql.product.pricing-info/summary string?)
(s/def :gcp.cloud-sql.product.pricing-info/currency-conversion-rate int?)
(s/def :gcp.cloud-sql.product.pricing-info/effective-time string?)

(s/def :gcp.cloud-sql.product.pricing-info.pricing-expression/usage-unit #{"d" "GiBy.mo" "GiBy" "GiBy.h" "h"})
(s/def :gcp.cloud-sql.product.pricing-info.pricing-expression/usage-unit-description #{"gibibyte hour" "hour" "gibibyte month" "gibibyte" "day"})
(s/def :gcp.cloud-sql.product.pricing-info.pricing-expression/base-unit #{"s" "By.s" "By"})
(s/def :gcp.cloud-sql.product.pricing-info.pricing-expression/base-unit-description #{"byte second" "byte" "second"})
(s/def :gcp.cloud-sql.product.pricing-info.pricing-expression/base-unit-conversion-factor
  (s/conformer #(try (double %)
                  (catch java.lang.ClassCastException _ ::s/invalid))))
(s/def :gcp.cloud-sql.product.pricing-info.pricing-expression/display-quantity int?)

(s/def :gcp.cloud-sql.product.pricing-info.pricing-expression.tiered-rate/start-usage-amount int?)
(s/def :gcp.cloud-sql.product.pricing-info.pricing-expression.tiered-rate.unit-price/currency-code #{"USD"})
(s/def :gcp.cloud-sql.product.pricing-info.pricing-expression.tiered-rate.unit-price/units string?)
(s/def :gcp.cloud-sql.product.pricing-info.pricing-expression.tiered-rate.unit-price/nanos int?)

(s/def :gcp.cloud-sql.product.pricing-info.pricing-expression.tiered-rate/unit-price
  (h/strictly-conform-json-object2
    unit-price-spec
    {"currencyCode"
     [:gcp.product.pricing-info.pricing-expression.tiered-rate.unit-price/currency-code
      :gcp.cloud-sql.product.pricing-info.pricing-expression.tiered-rate.unit-price/currency-code],
     "units"
     [:gcp.product.pricing-info.pricing-expression.tiered-rate.unit-price/units
      :gcp.cloud-sql.product.pricing-info.pricing-expression.tiered-rate.unit-price/units],
     "nanos"
     [:gcp.product.pricing-info.pricing-expression.tiered-rate.unit-price/nanos
      :gcp.cloud-sql.product.pricing-info.pricing-expression.tiered-rate.unit-price/nanos]}))

(s/def :gcp.cloud-sql.product.pricing-info.pricing-expression/tiered-rates
  (h/strictly-conform-json-object2
    tiered-rates-spec
    {"startUsageAmount"
     [:gcp.product.pricing-info.pricing-expression.tiered-rate/start-usage-amount
      :gcp.cloud-sql.product.pricing-info.pricing-expression.tiered-rate/start-usage-amount],
     "unitPrice"
     [:gcp.product.pricing-info.pricing-expression.tiered-rate/unit-price
      :gcp.cloud-sql.product.pricing-info.pricing-expression.tiered-rate/unit-price]}))


(s/def :gcp.cloud-sql.product.pricing-info/pricing-expression
  (h/strictly-conform-json-object2
    pricing-expression-spec
    {"usageUnit"                [:gcp.product.pricing-info.pricing-expression/usage-unit :gcp.cloud-sql.product.pricing-info.pricing-expression/usage-unit],
     "usageUnitDescription"     [:gcp.product.pricing-info.pricing-expression/usage-unit-description :gcp.cloud-sql.product.pricing-info.pricing-expression/usage-unit-description]
     "baseUnit"                 [:gcp.product.pricing-info.pricing-expression/base-unit :gcp.cloud-sql.product.pricing-info.pricing-expression/base-unit],
     "baseUnitDescription"      [:gcp.product.pricing-info.pricing-expression/base-unit-description :gcp.cloud-sql.product.pricing-info.pricing-expression/base-unit-description],
     "baseUnitConversionFactor" [:gcp.product.pricing-info.pricing-expression/base-unit-conversion-factor :gcp.cloud-sql.product.pricing-info.pricing-expression/base-unit-conversion-factor],
     "displayQuantity"          [:gcp.product.pricing-info.pricing-expression/display-quantity :gcp.cloud-sql.product.pricing-info.pricing-expression/display-quantity],
     "tieredRates"              [:gcp.product.pricing-info.pricing-expression/tiered-rates (s/coll-of :gcp.cloud-sql.product.pricing-info.pricing-expression/tiered-rates)]}))

(s/def :gcp.cloud-sql.product/pricing-info
  (h/strictly-conform-json-object2
    pricing-info-spec
    {"summary"                [:gcp.product.pricing-info/summary :gcp.cloud-sql.product.pricing-info/summary],
     "pricingExpression"      [:gcp.product.pricing-info/pricing-expression :gcp.cloud-sql.product.pricing-info/pricing-expression],
     "currencyConversionRate" [:gcp.product.pricing-info/currency-conversion-rate :gcp.cloud-sql.product.pricing-info/currency-conversion-rate],
     "effectiveTime"          [:gcp.product.pricing-info/effective-time :gcp.cloud-sql.product.pricing-info/effective-time]}))

(s/def :gcp.cloud-sql/product
  (h/strictly-conform-json-object2
    product-spec
    {"name"                [:gcp.product/name :gcp.cloud-sql.product/name],
     "skuId"               [:gcp.product/sku-id :gcp.cloud-sql.product/sku-id],
     "description"         [:gcp.product/description :gcp.cloud-sql.product/description],
     "category"            [:gcp.product/category :gcp.cloud-sql.product/category],
     "serviceRegions"      [:gcp.product/service-regions (s/coll-of :gcp.cloud-sql.product/service-regions)],
     "pricingInfo"         [:gcp.product/pricing-info (s/coll-of :gcp.cloud-sql.product/pricing-info)],
     "serviceProviderName" [:gcp.product/service-provider-name :gcp.cloud-sql.product/service-provider-name]
     "geoTaxonomy"         [nil map?]}))

(def description-heuristics
  [[#"Cloud SQL for (PostgreSQL|SQL Server|MySQL): (Zonal|Regional) - *((([0-9]+)? *vCPU)?[ +]*((?:([0-9\.]+)GB)? *RAM)?|Low cost storage|IP address reservation|Micro instance|Small instance|Standard storage)? in (.+?)"
    (fn [{{resource-group :gcp.product.category/resource-group} :gcp.product/category}
         db-type zonal-or-regional flags cpu cpu-count ram ram-gb region]
      {:cs.gcp.cloud-sql/isHA                        (case zonal-or-regional
                                                       "Zonal" false
                                                       "Regional" true)
       :cs.gcp.cloud-sql/db-type                     db-type
       :cs.gcp.cloud-sql/machine-family              (case resource-group
                                                       ("SQLGen2InstancesN1Highmem" "SQLGen2InstancesN1Standard")
                                                       :cs.gcp.cloud-sql.machine-family/intel-n1
                                                       ("SQLGen2InstancesF1Micro" "SQLGen2InstancesG1Small")
                                                       :cs.gcp.cloud-sql.machine-family/shared
                                                       nil)
       :cs.gcp.cloud-sql/machine-type                (case resource-group
                                                       "SQLGen2InstancesN1Highmem"
                                                       :cs.gcp.cloud-sql.machine-type/highmem
                                                       "SQLGen2InstancesN1Standard"
                                                       :cs.gcp.cloud-sql.machine-type/standard
                                                       ("SQLGen2InstancesCPU" "SQLGen2InstancesRAM")
                                                       :cs.gcp.cloud-sql.machine-type/custom
                                                       ("SQLGen2InstancesF1Micro" "SQLGen2InstancesG1Small")
                                                       :cs.gcp.cloud-sql.machine-type/generic
                                                       nil)
       :cs.gcp.cloud-sql/component                   (case resource-group
                                                       "SQLGen2InstancesCPU" :cs.gcp.cloud-sql.component/cpu
                                                       "SQLGen2InstancesRAM" :cs.gcp.cloud-sql.component/ram
                                                       (case flags
                                                         ("Low cost storage" "Standard storage") :cs.gcp.cloud-sql.component/storage
                                                         "IP address reservation" :cs.gcp.cloud-sql.component/ip-address
                                                         nil))
       :cs.gcp.cloud-sql.component.cpu/cpu-number    (some-> cpu-count Integer/parseInt)
       :cs.gcp.cloud-sql.component.ram/ram-gb        (some-> ram-gb Double/parseDouble)
       :cs.gcp.cloud-sql/shared-core-type            (case resource-group
                                                       "SQLGen2InstancesF1Micro" :cs.gcp.cloud-sql.shared-core-type/f1-micro
                                                       "SQLGen2InstancesG1Small" :cs.gcp.cloud-sql.shared-core-type/g1-small
                                                       nil)
       :cs.gcp.cloud-sql.component.storage/disk-type (case resource-group
                                                       "PDStandard" :cs.gcp.cloud-sql.component.storage.disk-type/hdd
                                                       "SSD" :cs.gcp.cloud-sql.component.storage.disk-type/ssd
                                                       nil)})]

   [#"Network (Inter Region|Internet|Google|Inter Connect|Inter Zone) Egress(?: from ([\w-0-9 ]+) to ([\w-0-9 ]+)| (?:in )?([\w-0-9 ]+))?"
    (fn [{:keys [gcp.product.category/resource-group] :as product}
         network-type from-region to-region region]
      {:cs.gcp.cloud-sql/component                     :cs.gcp.cloud-sql.component/network
       :cs.gcp.cloud-sql.component.network/type        (case network-type
                                                         "Inter Region" :cs.gcp.cloud-sql.component.network.type/egress-inter-region
                                                         "Internet" :cs.gcp.cloud-sql.component.network.type/egress-internet
                                                         "Google" :cs.gcp.cloud-sql.component.network.type/egress-google
                                                         "Inter Connect" :cs.gcp.cloud-sql.component.network.type/egress-inter-connect
                                                         "Inter Zone" :cs.gcp.cloud-sql.component.network.type/egress-inter-zone)
       :cs.gcp.cloud-sql.component.network/from-region from-region
       :cs.gcp.cloud-sql.component.network/to-region   to-region
       :cs.gcp.cloud-sql.component.network/region      region})]
   [#"Storage PD (HDD|SSD|Snapshot)"
    (fn [{:keys [gcp.product.category/resource-group] :as product}
         disk-type]
      {:cs.gcp.cloud-sql/component                   :cs.gcp.cloud-sql.component/storage
       :cs.gcp.cloud-sql.component.storage/disk-type (case disk-type
                                                       "HDD" :cs.gcp.cloud-sql.component.storage.disk-type/hdd
                                                       "SSD" :cs.gcp.cloud-sql.component.storage.disk-type/ssd
                                                       "Snapshot" :cs.gcp.cloud-sql.component.storage.disk-type/snapshot
                                                       nil)})]
   [#"Cloud SQL for MySQL: Read Replica \(free with promotional discount until September 2021\) - (Low cost storage|RAM|Standard storage|vCPU \+ RAM|vCPU) in ([\w-0-9 ]+)"
    (fn [{:keys [gcp.product.category/resource-group] :as product}
         flags region]
      ;; Uncomment if there's any use in trying to understand promotional offers
      #_{:cs.gcp.cloud-sql/db-type                     "MySQL"
         :cs.gcp.cloud-sql/machine-type                (case flag
                                                         ("RAM" "vCPU" "vCPU + RAM")
                                                         :cs.gcp.cloud-sql.machine-type/standard
                                                         nil)
         :cs.gcp.cloud-sql/component                   (case flag
                                                         "Low cost storage" :cs.gcp.cloud-sql.component/storage
                                                         "RAM" :cs.gcp.cloud-sql.component/ram
                                                         "Standard storage" :cs.gcp.cloud-sql.component/storage
                                                         "vCPU + RAM" nil
                                                         "vCPU" :cs.gcp.cloud-sql.component/cpu)
         :cs.gcp.cloud-sql.component.storage/disk-type (case flag
                                                         "Low cost storage" :cs.gcp.cloud-sql.component.storage.disk-type/hdd
                                                         "Standard storage" :cs.gcp.cloud-sql.component.storage.disk-type/ssd
                                                         nil)}
      {})]
   [#"Cloud SQL: Backups in ([\w-0-9 ]+)"
    (fn [{:keys [gcp.product.category/resource-group] :as product} _]
      {:cs.gcp.cloud-sql/component                   :cs.gcp.cloud-sql.component/storage
       :cs.gcp.cloud-sql.component.storage/disk-type :cs.gcp.cloud-sql.component.storage.disk-type/snapshot})]
   [#"Disk usage|D\d+(?: usage - hour)?"
    ;; discontinued: gen1 instances and their storage
    (constantly {})]
   [#"(External traffic)"
    (fn [{:keys [gcp.product.category/resource-group] :as product} _]
      {:cs.gcp.cloud-sql/component              :cs.gcp.cloud-sql.component/network
       :cs.gcp.cloud-sql.component.network/type :cs.gcp.cloud-sql.component.network.type/external-traffic})]
   [#"IP address idling (?:-|in) (hour|seconds)"
    (fn [{:keys [gcp.product.category/resource-group] :as product} hour-second]
      {:cs.gcp.cloud-sql/component (case hour-second
                                     "hour" :cs.gcp.cloud-sql.component/ip-address-idling-hour
                                     "seconds" :cs.gcp.cloud-sql.component/ip-address)})]
   [#"Commitment - dollar based v1: Cloud SQL database ([-a-z0-9]+) for (\d+) years?"
    ;; related to gen1 (which is discontinued)
    (constantly {})]])

(defn apply-description-heuristics [cases {:keys [gcp.product/description] :as product}]
  (some
    #(let [[p & flags] %]
       (try (when-some [match (re-matches p description)]
              (transduce (map (fn [attr-or-m-or-f]
                                (cond
                                  (fn? attr-or-m-or-f)
                                  (let [m (apply attr-or-m-or-f product (next match))]
                                    ; remove nil valued entries
                                    (transduce (keep (fn [[k v]] (when (nil? v) k))) dissoc m m))
                                  (map? attr-or-m-or-f) attr-or-m-or-f
                                  :else {attr-or-m-or-f true})))
                into
                {}
                flags))
         (catch Exception e)))
    cases))

(defn do-sku
  [product]
  (let [product-c (h/conform! :gcp.cloud-sql/product product {})
        m (apply-description-heuristics description-heuristics product-c)]
    (when (nil? m)
      (throw (ex-info "Description not recognized" {:product product})))
    (-> product-c common/inline-sub-maps (merge m))))

(comment

  (def client (d/client {:server-type        :peer-server
                         :access-key         "myaccesskey"
                         :secret             "mysecret"
                         :endpoint           "localhost:8999"
                         :validate-hostnames false}))

  (def conn (d/connect client {:db-name "workspace"}))
  (def db (d/db conn))


  ;; CPU SQL Server with discount
  (d/q '[:find (pull ?e [*])
         :where
         [?e :gcp.product/service-regions "us-central1"]
         [?e :cs.gcp.cloud-sql/db-type "SQL Server DB"]
         [?e :cs.gcp.cloud-sql/isHA false]
         #_[?e :cs.gcp.cloud-sql/machine-family :cs.gcp.cloud-sql.machine-family/shared]
         #_[?e :cs.gcp.cloud-sql/machine-type :cs.gcp.cloud-sql.machine-type/generic]
         [?e :cs.gcp.cloud-sql/component :cs.gcp.cloud-sql.component/cpu]]
    db)

  (qbe/q '#:cs.gcp.cloud-sql
          {:gcp.product/service-regions "us-central1"
           :gcp.product/description     ?desc
           :db-type                     "SQL Server DB"
           :isHA                        false
           :component                   :cs.gcp.cloud-sql.component/cpu
           :gcp.product/pricing-info
           #:gcp.product.pricing-info.pricing-expression
                   {:usage-unit ?unit
                    :tiered-rates
                    #:gcp.product.pricing-info.pricing-expression.tiered-rate
                            {:start-usage-amount 0          ; only first tier
                             :unit-price         ?price}}}
    db)
  ; returns
  [{:price 0.0413, :unit "h", :desc "SQL Server DB custom CORE running in NA (with 30% promotional discount)"}
   {:price 0.035, :unit "h", :desc "SQL Server DB generic Small instance with 1 VCPU running in NA (with 30% promotional discount)"}]

  ;; HA CPU SQL Server with discount
  (d/q '[:find (pull ?e [*])
         :where
         [?e :gcp.product/service-regions "us-central1"]
         [?e :cs.gcp.cloud-sql/db-type "SQL Server DB"]
         [?e :cs.gcp.cloud-sql/isHA true]
         #_[?e :cs.gcp.cloud-sql/machine-family :cs.gcp.cloud-sql.machine-family/shared]
         #_[?e :cs.gcp.cloud-sql/machine-type :cs.gcp.cloud-sql.machine-type/generic]
         [?e :cs.gcp.cloud-sql/component :cs.gcp.cloud-sql.component/cpu]]
    db)



  (d/q '[:find (pull ?e [*])
         :where
         [?e :gcp.product/service-regions "us-central1"]
         [?e :cs.gcp.cloud-sql/db-type "SQL Server DB"]
         [?e :cs.gcp.cloud-sql/isHA false]
         #_[?e :cs.gcp.cloud-sql/machine-family :cs.gcp.cloud-sql.machine-family/shared]
         #_[?e :cs.gcp.cloud-sql/machine-type :cs.gcp.cloud-sql.machine-type/generic]
         [?e :cs.gcp.cloud-sql/component :cs.gcp.cloud-sql.component/ram]]
    db)

  (d/q '[:find (pull ?e [*])
         :where
         [?e :gcp.product/service-regions "us-central1"]
         [?e :cs.gcp.cloud-sql/db-type "SQL Server DB"]
         [?e :cs.gcp.cloud-sql/isHA true]
         #_[?e :cs.gcp.cloud-sql/machine-family :cs.gcp.cloud-sql.machine-family/shared]
         #_[?e :cs.gcp.cloud-sql/machine-type :cs.gcp.cloud-sql.machine-type/generic]
         [?e :cs.gcp.cloud-sql/component :cs.gcp.cloud-sql.component/storage]]
    db)

  (d/q '[:find (pull ?e [*])
         :where
         [?e :gcp.product/service-regions "us-central1"]
         [?e :cs.gcp.cloud-sql/db-type "SQL Server DB"]
         [?e :cs.gcp.cloud-sql/isHA true]
         #_[?e :cs.gcp.cloud-sql/machine-family :cs.gcp.cloud-sql.machine-family/shared]
         #_[?e :cs.gcp.cloud-sql/machine-type :cs.gcp.cloud-sql.machine-type/generic]
         [?e :cs.gcp.cloud-sql/component :cs.gcp.cloud-sql.component/storage]]
    db)

  ;; Storage every-where
  (d/q '[:find (pull ?e [*])
         :where
         [?e :gcp.product/service-regions "us-central1"]
         #_[?e :cs.gcp.cloud-sql/db-type "SQL Server DB"]
         #_[?e :cs.gcp.cloud-sql/isHA true]
         #_[?e :cs.gcp.cloud-sql/machine-family :cs.gcp.cloud-sql.machine-family/shared]
         #_[?e :cs.gcp.cloud-sql/machine-type :cs.gcp.cloud-sql.machine-type/generic]
         [?e :cs.gcp.cloud-sql/component :cs.gcp.cloud-sql.component/storage]
         [?e :cs.gcp.cloud-sql.component.storage/disk-type :cs.gcp.cloud-sql.component.storage.disk-type/snapshot]]
    db)

  )
