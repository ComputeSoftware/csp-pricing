(ns computesoftware.csp-pricing.gcp.ce-disk-image
  (:require
    [clojure.spec.alpha :as s]
    [computesoftware.csp-pricing.gcp.common :as common]
    [computesoftware.csp-pricing.impl.spec-helpers :as h]
    [datomic.client.api :as d]
    [kwill.anomkit :as ak]))

(s/def :cs.gcp.compute/component
  #{:cs.gcp.compute.component/cpu
    :cs.gcp.compute.component/gpu
    :cs.gcp.compute.component/ram
    :cs.gcp.compute.component/storage})
(s/def :cs.gcp.compute/machine-type
  #{:cs.gcp.compute.machine-type/c2
    :cs.gcp.compute.machine-type/c2d
    :cs.gcp.compute.machine-type/e2
    :cs.gcp.compute.machine-type/m1
    :cs.gcp.compute.machine-type/m2
    :cs.gcp.compute.machine-type/n1
    :cs.gcp.compute.machine-type/n2
    :cs.gcp.compute.machine-type/n2d
    :cs.gcp.compute.machine-type/t2d})
(s/def :cs.gcp.compute/isCustom keyword?)
(s/def :cs.gcp.compute/isExtended boolean?)
(s/def :cs.gcp.compute/license string?)
(s/def :cs.gcp.compute/shared-core-type #{:cs.gcp.compute.shared-core-type/f1-micro
                                          :cs.gcp.compute.shared-core-type/g1-small})
(s/def :cs.gcp.compute.component.cpu/min-cpu int?)
(s/def :cs.gcp.compute.component.cpu/max-cpu int?)
(s/def :cs.gcp.compute/hasMoreCpu boolean?)
(s/def :cs.gcp.compute.component.storage/type #{:cs.gcp.compute.component.storage.type/snapshot-storage
                                                :cs.gcp.compute.component.storage.type/multi-regional-snapshot-storage
                                                :cs.gcp.compute.component.storage.type/image-storage
                                                :cs.gcp.compute.component.storage.type/regional-standard-provisioned-space
                                                :cs.gcp.compute.component.storage.type/ssd-provisioned-space
                                                :cs.gcp.compute.component.storage.type/balanced-provisioned-space
                                                :cs.gcp.compute.component.storage.type/regional-balanced-provisioned-space
                                                :cs.gcp.compute.component.storage.type/local-ssd-provisioned-space
                                                :cs.gcp.compute.component.storage.type/standard-provisioned-space
                                                :cs.gcp.compute.component.storage.type/regional-ssd-provisioned-space
                                                :cs.gcp.compute.component.storage.type/machine-image})

(s/def :cs.gcp/compute (s/keys :opt [:cs.gcp.compute/component
                                     :cs.gcp.compute/machine-type
                                     :cs.gcp.compute/isCustom
                                     :cs.gcp.compute/isExtended
                                     :cs.gcp.compute/license
                                     :cs.gcp.compute/shared-core-type
                                     :cs.gcp.compute.component.cpu/min-cpu
                                     :cs.gcp.compute.component.cpu/max-cpu
                                     :cs.gcp.compute/hasMoreCpu
                                     :cs.gcp.compute.component.storage/type]))

(def schema
  [#:db{:ident       :cs.gcp.compute/component
        :valueType   :db.type/ref
        :cardinality :db.cardinality/one}
   {:db/ident :cs.gcp.compute.component/cpu}
   {:db/ident :cs.gcp.compute.component/gpu}
   {:db/ident :cs.gcp.compute.component/ram}
   {:db/ident :cs.gcp.compute.component/storage}

   #:db{:ident       :cs.gcp.compute/machine-type
        :valueType   :db.type/ref
        :cardinality :db.cardinality/many}
   {:db/ident :cs.gcp.compute.machine-type/n1}
   {:db/ident :cs.gcp.compute.machine-type/n2}
   {:db/ident :cs.gcp.compute.machine-type/n2d}
   {:db/ident :cs.gcp.compute.machine-type/m1}
   {:db/ident :cs.gcp.compute.machine-type/m2}
   {:db/ident :cs.gcp.compute.machine-type/c2}
   {:db/ident :cs.gcp.compute.machine-type/e2}
   {:db/ident :cs.gcp.compute.machine-type/t2d}
   {:db/ident :cs.gcp.compute.machine-type/c2d}

   #:db{:ident       :cs.gcp.compute/isCustom
        :valueType   :db.type/keyword
        :cardinality :db.cardinality/many}

   #:db{:ident       :cs.gcp.compute/isExtended
        :valueType   :db.type/boolean
        :cardinality :db.cardinality/one}

   ;; Licensing specific informations (part of Disk Image)
   #:db{:ident       :cs.gcp.compute/license
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :cs.gcp.compute/shared-core-type
        :valueType   :db.type/ref
        :cardinality :db.cardinality/one}
   {:db/ident :cs.gcp.compute.shared-core-type/g1-small}
   {:db/ident :cs.gcp.compute.shared-core-type/f1-micro}

   #:db{:ident       :cs.gcp.compute.component.cpu/min-cpu
        :valueType   :db.type/long
        :cardinality :db.cardinality/one}

   #:db{:ident       :cs.gcp.compute.component.cpu/max-cpu
        :valueType   :db.type/long
        :cardinality :db.cardinality/one}

   #:db{:ident       :cs.gcp.compute/hasMoreCpu
        :valueType   :db.type/boolean
        :cardinality :db.cardinality/one}

   ;; Disk informations
   #:db{:ident       :cs.gcp.compute.component.storage/type
        :valueType   :db.type/ref
        :cardinality :db.cardinality/one}
   {:db/ident :cs.gcp.compute.component.storage.type/machine-image}
   {:db/ident :cs.gcp.compute.component.storage.type/local-ssd-provisioned-space}
   {:db/ident :cs.gcp.compute.component.storage.type/image-storage}
   {:db/ident :cs.gcp.compute.component.storage.type/regional-standard-provisioned-space}
   {:db/ident :cs.gcp.compute.component.storage.type/standard-provisioned-space}
   {:db/ident :cs.gcp.compute.component.storage.type/ssd-provisioned-space}
   {:db/ident :cs.gcp.compute.component.storage.type/regional-ssd-provisioned-space}
   {:db/ident :cs.gcp.compute.component.storage.type/multi-regional-snapshot-storage}
   {:db/ident :cs.gcp.compute.component.storage.type/snapshot-storage}
   {:db/ident :cs.gcp.compute.component.storage.type/balanced-provisioned-space}
   {:db/ident :cs.gcp.compute.component.storage.type/regional-balanced-provisioned-space}])

(s/def :gcp.compute.product/name string?)
(s/def :gcp.compute.product/sku-id string?)
(s/def :gcp.compute.product/description string?)
(s/def :gcp.compute.product/service-regions
  #{"asia"
    "asia-east1"
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
    "europe"
    "europe-central2"
    "europe-north1"
    "europe-southwest1"
    "europe-west1"
    "europe-west2"
    "europe-west3"
    "europe-west4"
    "europe-west6"
    "europe-west8"
    "europe-west9"
    "global"
    "northamerica-northeast1"
    "northamerica-northeast2"
    "southamerica-east1"
    "southamerica-west1"
    "us"
    "us-central1"
    "us-central2"
    "us-east1"
    "us-east4"
    "us-east5"
    "us-east7"
    "us-south1"
    "us-west1"
    "us-west2"
    "us-west3"
    "us-west4"})
(s/def :gcp.compute.product/service-provider-name string?)
(s/def :gcp.compute.product.category/service-display-name #{"Compute Engine"})
(s/def :gcp.compute.product.category/resource-family #{"Storage" "Compute" "Network" "License"})
(s/def :gcp.compute.product.category/resource-group string?)
(s/def :gcp.compute.product.category/usage-type
  #{"Commit1Mo" "Commit1Yr" "Commit3Yr" "OnDemand" "Preemptible"})


(s/def :gcp.compute.product/category
  (h/strictly-conform-json-object2
    category-spec
    {"serviceDisplayName" [:gcp.product.category/service-display-name :gcp.compute.product.category/service-display-name],
     "resourceFamily"     [:gcp.product.category/resource-family :gcp.compute.product.category/resource-family],
     "resourceGroup"      [:gcp.product.category/resource-group :gcp.compute.product.category/resource-group],
     "usageType"          [:gcp.product.category/usage-type :gcp.compute.product.category/usage-type]}))

(s/def :gcp.compute.product.pricing-info/summary string?)
(s/def :gcp.compute.product.pricing-info/currency-conversion-rate int?)
(s/def :gcp.compute.product.pricing-info/effective-time string?)

(s/def :gcp.compute.product.pricing-info.pricing-expression/usage-unit #{"mo" "GiBy.mo" "count" "GiBy" "h" "GiBy.h"})
(s/def :gcp.compute.product.pricing-info.pricing-expression/usage-unit-description #{"gibibyte hour" "count" "hour" "gibibyte month" "gibibyte" "month"})
(s/def :gcp.compute.product.pricing-info.pricing-expression/base-unit #{"s" "By.s" "count" "By"})
(s/def :gcp.compute.product.pricing-info.pricing-expression/base-unit-description #{"byte second" "second" "count" "byte"})
(s/def :gcp.compute.product.pricing-info.pricing-expression/base-unit-conversion-factor
  (s/conformer #(try (double %)
                  (catch java.lang.ClassCastException _ ::s/invalid))))
(s/def :gcp.compute.product.pricing-info.pricing-expression/display-quantity int?)

(s/def :gcp.compute.product.pricing-info.pricing-expression.tiered-rate/start-usage-amount int?)
(s/def :gcp.compute.product.pricing-info.pricing-expression.tiered-rate.unit-price/currency-code #{"USD"})
(s/def :gcp.compute.product.pricing-info.pricing-expression.tiered-rate.unit-price/units string?)
(s/def :gcp.compute.product.pricing-info.pricing-expression.tiered-rate.unit-price/nanos int?)

(s/def :gcp.compute.product.pricing-info.pricing-expression.tiered-rate/unit-price
  (h/strictly-conform-json-object2
    unit-price-spec
    {"currencyCode"
     [:gcp.product.pricing-info.pricing-expression.tiered-rate.unit-price/currency-code
      :gcp.compute.product.pricing-info.pricing-expression.tiered-rate.unit-price/currency-code],
     "units"
     [:gcp.product.pricing-info.pricing-expression.tiered-rate.unit-price/units
      :gcp.compute.product.pricing-info.pricing-expression.tiered-rate.unit-price/units],
     "nanos"
     [:gcp.product.pricing-info.pricing-expression.tiered-rate.unit-price/nanos
      :gcp.compute.product.pricing-info.pricing-expression.tiered-rate.unit-price/nanos]}))

(s/def :gcp.compute.product.pricing-info.pricing-expression/tiered-rates
  (h/strictly-conform-json-object2
    tiered-rates-spec
    {"startUsageAmount"
     [:gcp.product.pricing-info.pricing-expression.tiered-rate/start-usage-amount
      :gcp.compute.product.pricing-info.pricing-expression.tiered-rate/start-usage-amount],
     "unitPrice"
     [:gcp.product.pricing-info.pricing-expression.tiered-rate/unit-price
      :gcp.compute.product.pricing-info.pricing-expression.tiered-rate/unit-price]}))


(s/def :gcp.compute.product.pricing-info/pricing-expression
  (h/strictly-conform-json-object2
    pricing-expression-spec
    {"usageUnit"                [:gcp.product.pricing-info.pricing-expression/usage-unit :gcp.compute.product.pricing-info.pricing-expression/usage-unit],
     "usageUnitDescription"     [:gcp.product.pricing-info.pricing-expression/usage-unit-description :gcp.compute.product.pricing-info.pricing-expression/usage-unit-description]
     "baseUnit"                 [:gcp.product.pricing-info.pricing-expression/base-unit :gcp.compute.product.pricing-info.pricing-expression/base-unit],
     "baseUnitDescription"      [:gcp.product.pricing-info.pricing-expression/base-unit-description :gcp.compute.product.pricing-info.pricing-expression/base-unit-description],
     "baseUnitConversionFactor" [:gcp.product.pricing-info.pricing-expression/base-unit-conversion-factor :gcp.compute.product.pricing-info.pricing-expression/base-unit-conversion-factor],
     "displayQuantity"          [:gcp.product.pricing-info.pricing-expression/display-quantity :gcp.compute.product.pricing-info.pricing-expression/display-quantity],
     "tieredRates"              [:gcp.product.pricing-info.pricing-expression/tiered-rates (s/coll-of :gcp.compute.product.pricing-info.pricing-expression/tiered-rates)]}))

(s/def :gcp.compute.product/pricing-info
  (h/strictly-conform-json-object2
    pricing-info-spec
    {"summary"                [:gcp.product.pricing-info/summary :gcp.compute.product.pricing-info/summary],
     "pricingExpression"      [:gcp.product.pricing-info/pricing-expression :gcp.compute.product.pricing-info/pricing-expression],
     "aggregationInfo"        [nil map? :opt]
     "currencyConversionRate" [:gcp.product.pricing-info/currency-conversion-rate :gcp.compute.product.pricing-info/currency-conversion-rate],
     "effectiveTime"          [:gcp.product.pricing-info/effective-time :gcp.compute.product.pricing-info/effective-time]}))

(s/def :gcp.compute/product
  (h/strictly-conform-json-object2
    product-spec
    {"name"                [:gcp.product/name :gcp.compute.product/name],
     "skuId"               [:gcp.product/sku-id :gcp.compute.product/sku-id],
     "description"         [:gcp.product/description :gcp.compute.product/description],
     "category"            [:gcp.product/category :gcp.compute.product/category],
     "serviceRegions"      [:gcp.product/service-regions (s/coll-of :gcp.compute.product/service-regions)],
     "pricingInfo"         [:gcp.product/pricing-info (s/coll-of :gcp.compute.product/pricing-info)],
     "serviceProviderName" [:gcp.product/service-provider-name :gcp.compute.product/service-provider-name]
     "geoTaxonomy"         [nil map? :opt]}))

(def description-heuristics
  [[#"(Preemptible )?(N1 Predefined Instance Core running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n1]}]

   [#"(Commitment v1: Cpu in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n1]
     :cs.gcp.compute/isCustom     [:yes :no]}]

   [#"(Preemptible )?(N1 Predefined Instance Ram running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n1]}]

   [#"(Commitment v1: Ram in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n1]
     :cs.gcp.compute/isCustom     [:yes :no]}]

   [#"(Preemptible )?(Custom Instance Core running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n1]
     :cs.gcp.compute/isCustom     [:yes]}]

   [#"(Preemptible )?(Custom Instance Ram running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n1]
     :cs.gcp.compute/isCustom     [:yes]}]

   [#"(Preemptible )?(Custom Extended Instance Ram running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n1]
     :cs.gcp.compute/isCustom     [:yes]}
    :cs.gcp.compute/isExtended]

   [#"(Preemptible )?(N2 Instance Core running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n2]}]

   [#"(Commitment v1: N2 Cpu in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n2]
     :cs.gcp.compute/isCustom     [:yes :no]}]

   [#"(Preemptible )?(N2 Instance Ram running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n2]}]

   [#"(Commitment v1: N2 Ram in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n2]
     :cs.gcp.compute/isCustom     [:yes :no]}]

   [#"(Preemptible )?(N2 Custom Instance Core running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n2]
     :cs.gcp.compute/isCustom     [:yes]}]

   [#"(Preemptible )?(N2 Custom Instance Ram running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n2]
     :cs.gcp.compute/isCustom     [:yes]}]

   [#"(Preemptible )?(N2 Custom Extended Instance Ram running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n2]
     :cs.gcp.compute/isCustom     [:yes]}
    :cs.gcp.compute/isExtended]

   [#"(Preemptible )?(N2D AMD Instance Core running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n2d]}]

   [#"(Commitment v1: N2D AMD Cpu in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n2d]
     :cs.gcp.compute/isCustom     [:yes :no]}]

   [#"(Preemptible )?(N2D AMD Instance Ram running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n2d]}]

   [#"(Commitment v1: N2D AMD Ram in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n2d]
     :cs.gcp.compute/isCustom     [:yes :no]}]

   [#"(Preemptible )?(N2D AMD Custom Instance Core running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n2d]
     :cs.gcp.compute/isCustom     [:yes]}]

   [#"(Preemptible )?(N2D AMD Custom Instance Ram running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n2d]
     :cs.gcp.compute/isCustom     [:yes]}]

   [#"(Preemptible )?(N2D AMD Custom Extended Ram running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/n2d]
     :cs.gcp.compute/isCustom     [:yes]}
    :cs.gcp.compute/isExtended]

   [#"(Preemptible )?(E2 Instance Core running in|Commitment v1: E2 Cpu in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/e2]
     :cs.gcp.compute/isCustom     [:yes :no]}]

   [#"(Preemptible )?(E2 Instance Ram running in|Commitment v1: E2 Ram in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/e2]
     :cs.gcp.compute/isCustom     [:yes :no]}]
   [#"(Preemptible )?(T2D AMD Instance Core running in|Commitment v1: T2D AMD Cpu in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/t2d]}]

   [#"(Preemptible )?(T2D AMD Instance Ram running in|Commitment v1: T2D AMD Ram in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/t2d]}]

   [#"(Preemptible )?(Memory-optimized Instance Core running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/m1
                                   :cs.gcp.compute.machine-type/m2]}]

   [#"(Commitment v1: Memory-optimized Cpu in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/m1
                                   :cs.gcp.compute.machine-type/m2]
     :cs.gcp.compute/isCustom     [:yes :no]}]

   [#"(Preemptible )?(Memory-optimized Instance Ram running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/m1
                                   :cs.gcp.compute.machine-type/m2]}]

   [#"(Commitment v1: Memory-optimized Ram in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/m1
                                   :cs.gcp.compute.machine-type/m2]
     :cs.gcp.compute/isCustom     [:yes :no]}]

   [#"(Memory Optimized Upgrade Premium for Memory-optimized Instance Ram running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/m2]}]

   [#"(Memory Optimized Upgrade Premium for Memory-optimized Instance Core running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/m2]}]

   ;; Predefined c2d CPU
   [#"(C2D AMD Instance Core) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/c2d]}]

   ;; Predefined c2d RAM
   [#"(C2D AMD Instance Ram) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/c2d]}]

   ;; Custom c2d CPU
   [#"(C2D AMD Custom Instance Core) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/c2d]
     :cs.gcp.compute/isCustom     [:yes]}]

   ;; Custom c2d RAM
   [#"(C2D AMD Custom Instance Ram) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/c2d]
     :cs.gcp.compute/isCustom     [:yes]}]

   [#"(Preemptible |Commitment: )?(Compute optimized Core running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/cpu
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/c2]}]

   [#"(Preemptible |Commitment: )?(Compute optimized Ram running in) .*"
    {:cs.gcp.compute/component    :cs.gcp.compute.component/ram
     :cs.gcp.compute/machine-type [:cs.gcp.compute.machine-type/c2]}]

   ;; Licensing Informations
   [#"Licensing Fee for (.*) (?:\(([-\w]+) cost\)|on (?:([-\w]+)|VM with (\d+)(?: to (\d+)| (or more))? VCPU))"
    (fn [license ram-cpu instance min-cpu max-cpu or-more-cpu]
      (cond-> {:cs.gcp.compute/license               license
               :cs.gcp.compute/component             (case ram-cpu
                                                       nil nil
                                                       "RAM" :cs.gcp.compute.component/ram
                                                       "CPU" :cs.gcp.compute.component/cpu
                                                       "GPU" :cs.gcp.compute.component/gpu)
               :cs.gcp.compute/shared-core-type      (case instance
                                                       nil nil
                                                       "g1-small" :cs.gcp.compute.shared-core-type/g1-small
                                                       "f1-micro" :cs.gcp.compute.shared-core-type/f1-micro)
               :cs.gcp.compute.component.cpu/min-cpu (some-> min-cpu Integer/parseInt)
               :cs.gcp.compute.component.cpu/max-cpu (some-> max-cpu Integer/parseInt)
               :cs.gcp.compute/hasMoreCpu            (when or-more-cpu true)}
        (or min-cpu max-cpu or-more-cpu) (assoc :cs.gcp.compute/component
                                           :cs.gcp.compute.component/cpu)))]

   ;; Disk informations
   [#"(Storage PD Capacity)( in)?.*"
    {:cs.gcp.compute/component              :cs.gcp.compute.component/storage
     :cs.gcp.compute.component.storage/type :cs.gcp.compute.component.storage.type/standard-provisioned-space}]

   [#"(SSD backed PD Capacity)( in)?.*"
    {:cs.gcp.compute/component              :cs.gcp.compute.component/storage
     :cs.gcp.compute.component.storage/type :cs.gcp.compute.component.storage.type/ssd-provisioned-space}]

   [#"(Regional Storage PD Capacity)( in)?.*"
    {:cs.gcp.compute/component              :cs.gcp.compute.component/storage
     :cs.gcp.compute.component.storage/type :cs.gcp.compute.component.storage.type/regional-standard-provisioned-space}]

   [#"(Regional SSD backed PD Capacity)( in)?.*"
    {:cs.gcp.compute/component              :cs.gcp.compute.component/storage
     :cs.gcp.compute.component.storage/type :cs.gcp.compute.component.storage.type/regional-ssd-provisioned-space}]

   [#"(Balanced PD Capacity)( in)?.*"
    {:cs.gcp.compute/component              :cs.gcp.compute.component/storage
     :cs.gcp.compute.component.storage/type :cs.gcp.compute.component.storage.type/balanced-provisioned-space}]

   [#"(Regional Balanced PD Capacity)( in)?.*"
    {:cs.gcp.compute/component              :cs.gcp.compute.component/storage
     :cs.gcp.compute.component.storage/type :cs.gcp.compute.component.storage.type/regional-balanced-provisioned-space}]

   [#"(Storage PD Snapshot in).*"
    {:cs.gcp.compute/component              :cs.gcp.compute.component/storage
     :cs.gcp.compute.component.storage/type :cs.gcp.compute.component.storage.type/snapshot-storage}]

   [#"Storage PD Snapshot"
    {:cs.gcp.compute/component              :cs.gcp.compute.component/storage
     :cs.gcp.compute.component.storage/type :cs.gcp.compute.component.storage.type/multi-regional-snapshot-storage}]

   [#"(SSD backed Local Storage)( attached to Preemptible VMs)?( in.*)?"
    {:cs.gcp.compute/component              :cs.gcp.compute.component/storage
     :cs.gcp.compute.component.storage/type :cs.gcp.compute.component.storage.type/local-ssd-provisioned-space}]

   [#"(Commitment v1: Local SSD)( in)?.*"
    {:cs.gcp.compute/component              :cs.gcp.compute.component/storage
     :cs.gcp.compute.component.storage/type :cs.gcp.compute.component.storage.type/local-ssd-provisioned-space}]

   [#"(Storage Image)( in)?(.*)"
    {:cs.gcp.compute/component              :cs.gcp.compute.component/storage
     :cs.gcp.compute.component.storage/type :cs.gcp.compute.component.storage.type/image-storage}]

   [#"(Storage Machine Image)( in)?(.*)"
    {:cs.gcp.compute/component              :cs.gcp.compute.component/storage
     :cs.gcp.compute.component.storage/type :cs.gcp.compute.component.storage.type/machine-image}]])

(defn apply-description-heuristics [cases description]
  (some
    #(let [[p & flags] %]
       (when-some [match (re-matches p description)]
         (transduce (map (fn [attr-or-m-or-f]
                           (cond
                             (fn? attr-or-m-or-f)
                             (let [m (apply attr-or-m-or-f (next match))]
                               ; remove nil valued entries
                               (transduce (keep (fn [[k v]] (when (nil? v) k))) dissoc m m))
                             (map? attr-or-m-or-f) attr-or-m-or-f
                             :else {attr-or-m-or-f true})))
           into
           {:cs.gcp.compute/isCustom   [:no]
            :cs.gcp.compute/isExtended false}
           flags)))
    cases))

(defn do-sku
  [product]
  (let [product-c (h/conform! :gcp.compute/product product {})]
    (-> product-c common/inline-sub-maps
      (merge (apply-description-heuristics description-heuristics
               (:gcp.product/description product-c))))))

(defn machine-type->machine-type-list [machine-family]
  (into {}
    (group-by #(->> %
                 first
                 name
                 (re-matches #"(.*)(-\d+)")
                 second
                 keyword)
      (for [[machine classes] machine-family
            [class {:keys [base multipliers]}] classes
            m multipliers]
        [(keyword (str (name machine) "-" (name class) "-" m))
         (into {} (map (fn [[k v]] [k (* m v)])) base)]))))

(def legacy-gcp->gcp-machine-types {:gcp-instance.family/n1  :cs.gcp.compute.machine-type/n1
                                    :gcp-instance.family/c2  :cs.gcp.compute.machine-type/c2
                                    :gcp-instance.family/m2  :cs.gcp.compute.machine-type/m2
                                    :gcp-instance.family/m1  :cs.gcp.compute.machine-type/m1
                                    :gcp-instance.family/e2  :cs.gcp.compute.machine-type/e2
                                    :gcp-instance.family/n2d :cs.gcp.compute.machine-type/n2d
                                    :gcp-instance.family/n2  :cs.gcp.compute.machine-type/n2})

(def legacy-gcp-class->gcp-class {:gcp-instance.class/on-demand   "OnDemand"
                                  :gcp-instance.class/preemptible "Preemptible"
                                  :gcp-instance.class/commit1y    "Commit1Yr"
                                  :gcp-instance.class/commit3y    "Commit3Yr"})

(def predefined-machines
  "Taken from machine family doc https://cloud.google.com/compute/docs/machine-types."
  #:cs.gcp.compute.machine-type
          {:c2  {:standard {:base        {:vcpu   1
                                          :memory 4.0}
                            :multipliers [4 8 16 30 60]}}
           :c2d {:standard {:base        {:vcpu   4
                                          :memory 16.0}
                            :multipliers [4 8 16 30 60]}}
           :m2  {:ultramem {:base        {:vcpu   208
                                          :memory 5888.0}
                            :multipliers [1 2]}}
           :m1  {:ultramem {:base        {:vcpu   40
                                          :memory 961.0}
                            :multipliers [1 2 4]}
                 :megamem  {:base        {:vcpu   96
                                          :memory 1433.6}
                            :multipliers [1]}}
           :e2  {:micro    {:base        {:vcpu         2
                                          :memory       1.0
                                          :allowed-vcpu 0.125}
                            :multipliers [1]}
                 :small    {:base        {:vcpu         2
                                          :memory       2.0
                                          :allowed-vcpu 0.25}
                            :multipliers [1]}
                 :medium   {:base        {:vcpu         2
                                          :memory       4.0
                                          :allowed-vcpu 0.50}
                            :multipliers [1]}
                 :standard {:base        {:vcpu   1
                                          :memory 4.0}
                            :multipliers [2 4 8 16]}
                 :highmem  {:base        {:vcpu   1
                                          :memory 8.0}
                            :multipliers [2 4 8 16]}
                 :highcpu  {:base        {:vcpu   1
                                          :memory 1.0}
                            :multipliers [2 4 8 16]}}
           :n2d {:standard {:base        {:vcpu   1
                                          :memory 4.0}
                            :multipliers [2 4 8 16 32 48 64 80 96 128 224]}
                 :highmem  {:base        {:vcpu   1
                                          :memory 8.0}
                            :multipliers [2 4 8 16 32 48 64 80 96]}
                 :highcpu  {:base        {:vcpu   1
                                          :memory 1.0}
                            :multipliers [2 4 8 16 32 48 64 80 96 128 224]}}
           :n2  {:standard {:base        {:vcpu   1
                                          :memory 4.0}
                            :multipliers [2 4 8 16 32 48 64 80]}
                 :highmem  {:base        {:vcpu   1
                                          :memory 8.0}
                            :multipliers [2 4 8 16 32 48 64 80]}
                 :highcpu  {:base        {:vcpu   1
                                          :memory 1.0}
                            :multipliers [2 4 8 16 32 48 64 80]}}
           :t2d {:standard {:base        {:vcpu   1
                                          :memory 4.0}
                            :multipliers [1 2 4 8 16 32 48 60]}}
           :n1  {:standard {:base        {:vcpu   1
                                          :memory 3.75}
                            :multipliers [1 2 4 8 16 32 64 96]}
                 :highmem  {:base        {:vcpu   1
                                          :memory 6.5}
                            :multipliers [2 4 8 16 32 64 96]}
                 :highcpu  {:base        {:vcpu   1
                                          :memory 0.9}
                            :multipliers [2 4 8 16 32 64 96]}}})

(def allowable-memory-gb-per-vcpu
  "Map of machine family to amount of memory gb per vcpu that is allowed by
  default. Any memory past this amount is billed at the extended memory rate.
  As of 2021/08/30, extended memory is not available for E2 machine types.
  https://cloud.google.com/compute/docs/instances/creating-instance-with-custom-machine-type#extendedmemory"
  {:cs.gcp.compute.machine-type/n1  6.5
   :cs.gcp.compute.machine-type/n2  8.0
   :cs.gcp.compute.machine-type/n2d 8.0})

(defn instance-price-per-hour
  [db {:resource-price.gcp-instance/keys [region class family vcpu memory-mb shared-core-type custom?] :as resource-price}]
  (let [gc-usage-type (legacy-gcp-class->gcp-class class)
        gc-shared-core-type (some-> shared-core-type {"f1-micro" "F1Micro" "g1-small" "G1Small"})
        memory-gb (/ memory-mb 1024.0)
        base-facts '[[?e :gcp.product/service-provider-name "Google"]
                     [?e :gcp.product/service-regions ?gc-region]
                     [?e :gcp.product.category/resource-family "Compute"]
                     [?e :gcp.product.category/usage-type ?gc-usage-type]
                     [?e :gcp.product/pricing-info ?pi]
                     [?pi :gcp.product.pricing-info.pricing-expression/tiered-rates ?tr]
                     [?tr :gcp.product.pricing-info.pricing-expression.tiered-rate/unit-price ?price]]
        base-query (cond
                     gc-shared-core-type (conj base-facts '[?e :gcp.product.category/resource-group ?gc-shared-core-type])
                     custom? (conj base-facts
                               '[?e :cs.gcp.compute/machine-type ?gc-family]
                               '[?e :cs.gcp.compute/isCustom :yes]
                               '[?e :cs.gcp.compute/isExtended false])
                     (not custom?) (conj base-facts
                                     '[?e :cs.gcp.compute/machine-type ?gc-family]
                                     '[?e :cs.gcp.compute/isCustom :no]
                                     '[?e :cs.gcp.compute/isExtended false]))]
    (if gc-shared-core-type
      (ffirst
        (d/q (into '[:find (sum ?price)
                     :in $ ?gc-region ?gc-usage-type ?gc-shared-core-type
                     :where]
               base-query)
          db region gc-usage-type gc-shared-core-type))
      (let [family (legacy-gcp->gcp-machine-types family)
            vcpu-prices
            (map first
              (d/q (into '[:find (sum ?price)
                           :in $ ?gc-region ?gc-usage-type ?gc-family
                           :where
                           [?e :cs.gcp.compute/component :cs.gcp.compute.component/cpu]]
                     base-query)
                db region gc-usage-type family))
            memory-prices
            (map first
              (d/q (into '[:find (sum ?price)
                           :in $ ?gc-region ?gc-usage-type ?gc-family
                           :where
                           [?e :cs.gcp.compute/component :cs.gcp.compute.component/ram]]
                     base-query)
                db region gc-usage-type family))]
        (cond
          (not (and (seq vcpu-prices) (seq memory-prices)))
          (ak/not-found "No matching prices found." {:resource-price resource-price})
          (not custom?)
          ;;@NOTE : we still check if the standard machine exists in the table
          (let [all-predefined-machines
                (into #{}
                  (comp (map val) cat
                    (map (fn [[_ {:keys [vcpu memory allowed-vcpu] :or {allowed-vcpu 1}}]] [vcpu memory allowed-vcpu])))
                  (machine-type->machine-type-list (select-keys predefined-machines [family])))]
            (if-let [[vcpu memory-gb allowed-vcpu] (some (fn [[v m a :as all]] (when (= [vcpu memory-gb] [v m]) all)) all-predefined-machines)]
              (if (or (next vcpu-prices) (next memory-prices))
                (ak/incorrect "Too many resource prices found."
                  {:vcpu-prices vcpu-prices :memory-prices memory-prices})
                (+ (* allowed-vcpu (* (first vcpu-prices) vcpu))
                  (* (first memory-prices) memory-gb)))
              (ak/not-found "No matching prices found." {:resource-price resource-price})))
          :else
          (let [extended-mem-vol (if-let [memory-threshold-lvl (allowable-memory-gb-per-vcpu family)]
                                   (cond (<= (* vcpu memory-threshold-lvl) memory-gb) (- memory-gb (* vcpu memory-threshold-lvl))
                                     :default 0) 0)
                extended-mem-prices (if (zero? extended-mem-vol)
                                      [0]
                                      (map first
                                        (d/q (into '[:find (sum ?price)
                                                     :in $ ?gc-region ?gc-usage-type
                                                     :where
                                                     [?e :cs.gcp.compute/machine-type ?gc-family]
                                                     [?e :cs.gcp.compute/isCustom :yes]
                                                     [?e :cs.gcp.compute/isExtended true]]
                                               base-facts)
                                          db region gc-usage-type)))]
            (cond
              (empty? extended-mem-prices) (ak/not-found "No matching prices found." {:resource-price resource-price})
              (or (next vcpu-prices) (next memory-prices) (next extended-mem-prices))
              (ak/incorrect "Too many resource prices found"
                {:vcpu-prices vcpu-prices :memory-prices memory-prices :extended-mem-prices extended-mem-prices})
              :else (+ (* (first vcpu-prices) vcpu)
                      (* (first memory-prices) (- memory-gb extended-mem-vol))
                      (* (first extended-mem-prices) extended-mem-vol)))))))))


(comment

  ;; @NOTE N1 : Predefined machines :okay
  ;; N1 Custom : okay
  ;; N1 extended : okay

  ;; N2 : Predefined machines okay
  ;; N2 Custom : okay
  ;; N2 extended : okay

  ;; N2D : Predefined machines okay
  ;; N2D Custom : Not good for OnDemand CPU : documentation is 5% higher than json prices for N2D-OnDemand
  ;; N2D extended : no prices found for OnDemand

  ;; E2 : not the same price for Preemptible so standard machines for E2 are fucked up

  ;; M1 good for predefined machines

  ;; M2 Good but no upgrade pricing other than the OnDemand one

  ;; C2 is good

  ;; f1-micro g1-small e2-shared-core are good

  (instance-price-per-hour
    db
    {:resource-price/provider               :resource-price.provider/gcp-instance
     :resource-price.gcp-instance/region    "us-central1"
     :resource-price.gcp-instance/class     :gcp-instance.class/on-demand
     :resource-price.gcp-instance/family    :gcp-instance.family/n1
     :resource-price.gcp-instance/vcpu      1
     :resource-price.gcp-instance/memory-mb 3840.0})

  (instance-price-per-hour db
    {:resource-price.gcp-instance/region    "asia-northeast1",
     :resource-price.gcp-instance/family    :gcp-instance.family/e2,
     :resource-price.gcp-instance/class     :gcp-instance.class/on-demand
     :resource-price.gcp-instance/vcpu      1
     :resource-price.gcp-instance/memory-mb 3840.0
     :resource-price/provider               :resource-price.provider/gcp-instance})

  0.04749975

  (instance-price-per-hour
    db
    {:resource-price/provider               :resource-price.provider/gcp-instance
     :resource-price.gcp-instance/region    "us-central1"
     :resource-price.gcp-instance/class     :gcp-instance.class/preemptible
     :resource-price.gcp-instance/family    :gcp-instance.family/n2
     :resource-price.gcp-instance/vcpu      2
     :resource-price.gcp-instance/memory-mb 8192.0})

  ;; works with preemptible
  0.023498

  (instance-price-per-hour
    db
    {:resource-price/provider               :resource-price.provider/gcp-instance
     :resource-price.gcp-instance/region    "us-central1"
     :resource-price.gcp-instance/class     :gcp-instance.class/on-demand
     :resource-price.gcp-instance/family    :gcp-instance.family/n1
     :resource-price.gcp-instance/vcpu      1
     :resource-price.gcp-instance/memory-mb 10240
     :resource-price.gcp-instance/custom?   true})

  ;; supports extended memory
  0.095498

  (instance-price-per-hour
    db
    {:resource-price/provider                      :resource-price.provider/gcp-instance
     :resource-price.gcp-instance/region           "us-central1"
     :resource-price.gcp-instance/class            :gcp-instance.class/on-demand
     :resource-price.gcp-instance/family           :gcp-instance.family/n1
     :resource-price.gcp-instance/vcpu             1
     :resource-price.gcp-instance/memory-mb        1024.0
     :resource-price.gcp-instance/shared-core-type "f1-micro"})

  ;; works with shared type
  0.007600000000000001

  (instance-price-per-hour
    db
    {:resource-price/provider               :resource-price.provider/gcp-instance
     :resource-price.gcp-instance/region    "us-central1"
     :resource-price.gcp-instance/class     :gcp-instance.class/on-demand
     :resource-price.gcp-instance/family    :gcp-instance.family/e2
     :resource-price.gcp-instance/vcpu      2
     :resource-price.gcp-instance/memory-mb 1024.0})

  ;; shared as E2 are "real" standard instances
  0.0083764275

  (instance-price-per-hour
    db
    {:resource-price/provider               :resource-price.provider/gcp-instance
     :resource-price.gcp-instance/region    "us-central1"
     :resource-price.gcp-instance/class     :gcp-instance.class/on-demand
     :resource-price.gcp-instance/family    :gcp-instance.family/m1
     :resource-price.gcp-instance/vcpu      40
     :resource-price.gcp-instance/memory-mb 984064.0})

  ;; memory prices
  6.293100000000001

  (instance-price-per-hour
    db
    {:resource-price/provider               :resource-price.provider/gcp-instance
     :resource-price.gcp-instance/region    "us-central1"
     :resource-price.gcp-instance/class     :gcp-instance.class/on-demand
     :resource-price.gcp-instance/family    :gcp-instance.family/n1
     :resource-price.gcp-instance/vcpu      1
     :resource-price.gcp-instance/memory-mb 10240.0
     :resource-price.gcp-instance/custom?   true})

  ;; Custom + extended
  0.095498)


