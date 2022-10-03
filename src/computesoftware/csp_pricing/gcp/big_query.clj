(ns computesoftware.csp-pricing.gcp.big-query
  (:require
    [datomic.client.api :as d]
    [clojure.spec.alpha :as s]
    [computesoftware.csp-pricing.gcp.common :as common]
    [computesoftware.csp-pricing.impl.spec-helpers :as h]))

(def schema [])

(s/def :gcp.big-query.product/name string?)
(s/def :gcp.big-query.product/sku-id string?)
(s/def :gcp.big-query.product/description string?)
(s/def :gcp.big-query.product/service-regions
  #{"asia-east1"
    "asia-east2"
    "asia-northeast1"
    "asia-northeast2"
    "asia-northeast3"
    "asia-south1"
    "asia-southeast1"
    "asia-southeast2"
    "australia-southeast1"
    "europe"
    "europe-central2"
    "europe-north1"
    "europe-west1"
    "europe-west2"
    "europe-west3"
    "europe-west4"
    "europe-west6"
    "global"
    "northamerica-northeast1"
    "southamerica-east1"
    "us"
    "us-central1"
    "us-east1"
    "us-east4"
    "us-west1"
    "us-west2"
    "us-west3"
    "us-west4"})
(s/def :gcp.big-query.product/service-provider-name #{"Google"})
(s/def :gcp.big-query.product.category/service-display-name #{"BigQuery" "BigQuery Reservation API" "BigQuery Storage API"})
(s/def :gcp.big-query.product.category/resource-family #{"ApplicationServices"})
(s/def :gcp.big-query.product.category/resource-group #{"ActiveStorage" "Streaming" "LongTermStorage" "OnDemandAnalysis" "FlatRate"})
(s/def :gcp.big-query.product.category/usage-type #{"OnDemand" "Commit1Mo" "Commit1Yr"})


(s/def :gcp.big-query.product/category
  (h/strictly-conform-json-object2
    category-spec
    {"serviceDisplayName" [:gcp.product.category/service-display-name :gcp.big-query.product.category/service-display-name],
     "resourceFamily"     [:gcp.product.category/resource-family :gcp.big-query.product.category/resource-family],
     "resourceGroup"      [:gcp.product.category/resource-group :gcp.big-query.product.category/resource-group],
     "usageType"          [:gcp.product.category/usage-type :gcp.big-query.product.category/usage-type]}))

(s/def :gcp.big-query.product.pricing-info/summary string?)
(s/def :gcp.big-query.product.pricing-info/currency-conversion-rate int?)
(s/def :gcp.big-query.product.pricing-info/effective-time string?)

(s/def :gcp.big-query.product.pricing-info.pricing-expression/usage-unit #{"TiBy" "mo" "h" "GiBy.mo" "GiBy" "MiBy"})
(s/def :gcp.big-query.product.pricing-info.pricing-expression/usage-unit-description #{"gibibyte month" "gibibyte" "hour" "tebibyte" "mebibyte" "month"})
(s/def :gcp.big-query.product.pricing-info.pricing-expression/base-unit #{"s" "By.s" "By"})
(s/def :gcp.big-query.product.pricing-info.pricing-expression/base-unit-description #{"byte second" "byte" "second"})
(s/def :gcp.big-query.product.pricing-info.pricing-expression/base-unit-conversion-factor
  (s/conformer #(try (double %)
                  (catch java.lang.ClassCastException _ ::s/invalid))))
(s/def :gcp.big-query.product.pricing-info.pricing-expression/display-quantity int?)

(s/def :gcp.big-query.product.pricing-info.pricing-expression.tiered-rate/start-usage-amount int?)
(s/def :gcp.big-query.product.pricing-info.pricing-expression.tiered-rate.unit-price/currency-code #{"USD"})
(s/def :gcp.big-query.product.pricing-info.pricing-expression.tiered-rate.unit-price/units string?)
(s/def :gcp.big-query.product.pricing-info.pricing-expression.tiered-rate.unit-price/nanos int?)

(s/def :gcp.big-query.product.pricing-info.pricing-expression.tiered-rate/unit-price
  (h/strictly-conform-json-object2
    unit-price-spec
    {"currencyCode"
     [:gcp.product.pricing-info.pricing-expression.tiered-rate.unit-price/currency-code
      :gcp.big-query.product.pricing-info.pricing-expression.tiered-rate.unit-price/currency-code],
     "units"
     [:gcp.product.pricing-info.pricing-expression.tiered-rate.unit-price/units
      :gcp.big-query.product.pricing-info.pricing-expression.tiered-rate.unit-price/units],
     "nanos"
     [:gcp.product.pricing-info.pricing-expression.tiered-rate.unit-price/nanos
      :gcp.big-query.product.pricing-info.pricing-expression.tiered-rate.unit-price/nanos]}))

(s/def :gcp.big-query.product.pricing-info.pricing-expression/tiered-rates
  (h/strictly-conform-json-object2
    tiered-rates-spec
    {"startUsageAmount"
     [:gcp.product.pricing-info.pricing-expression.tiered-rate/start-usage-amount
      :gcp.big-query.product.pricing-info.pricing-expression.tiered-rate/start-usage-amount],
     "unitPrice"
     [:gcp.product.pricing-info.pricing-expression.tiered-rate/unit-price
      :gcp.big-query.product.pricing-info.pricing-expression.tiered-rate/unit-price]}))


(s/def :gcp.big-query.product.pricing-info/pricing-expression
  (h/strictly-conform-json-object2
    pricing-expression-spec
    {"usageUnit"                [:gcp.product.pricing-info.pricing-expression/usage-unit :gcp.big-query.product.pricing-info.pricing-expression/usage-unit],
     "usageUnitDescription"     [:gcp.product.pricing-info.pricing-expression/usage-unit-description :gcp.big-query.product.pricing-info.pricing-expression/usage-unit-description]
     "baseUnit"                 [:gcp.product.pricing-info.pricing-expression/base-unit :gcp.big-query.product.pricing-info.pricing-expression/base-unit],
     "baseUnitDescription"      [:gcp.product.pricing-info.pricing-expression/base-unit-description :gcp.big-query.product.pricing-info.pricing-expression/base-unit-description],
     "baseUnitConversionFactor" [:gcp.product.pricing-info.pricing-expression/base-unit-conversion-factor :gcp.big-query.product.pricing-info.pricing-expression/base-unit-conversion-factor],
     "displayQuantity"          [:gcp.product.pricing-info.pricing-expression/display-quantity :gcp.big-query.product.pricing-info.pricing-expression/display-quantity],
     "tieredRates"              [:gcp.product.pricing-info.pricing-expression/tiered-rates (s/coll-of :gcp.big-query.product.pricing-info.pricing-expression/tiered-rates)]}))

(s/def :gcp.big-query.product/pricing-info
  (h/strictly-conform-json-object2
    pricing-info-spec
    {"summary"                [:gcp.product.pricing-info/summary :gcp.big-query.product.pricing-info/summary],
     "pricingExpression"      [:gcp.product.pricing-info/pricing-expression :gcp.big-query.product.pricing-info/pricing-expression],
     "aggregationInfo"        [nil map? :opt]
     "currencyConversionRate" [:gcp.product.pricing-info/currency-conversion-rate :gcp.big-query.product.pricing-info/currency-conversion-rate],
     "effectiveTime"          [:gcp.product.pricing-info/effective-time :gcp.big-query.product.pricing-info/effective-time]}))

(s/def :gcp.big-query/product
  (h/strictly-conform-json-object2
    product-spec
    {"name"                [:gcp.product/name :gcp.big-query.product/name],
     "skuId"               [:gcp.product/sku-id :gcp.big-query.product/sku-id],
     "description"         [:gcp.product/description :gcp.big-query.product/description],
     "category"            [:gcp.product/category :gcp.big-query.product/category],
     "serviceRegions"      [:gcp.product/service-regions (s/coll-of :gcp.big-query.product/service-regions)],
     "pricingInfo"         [:gcp.product/pricing-info (s/coll-of :gcp.big-query.product/pricing-info)],
     "serviceProviderName" [:gcp.product/service-provider-name :gcp.big-query.product/service-provider-name]
     "geoTaxonomy"         [nil map?]}))

(defn do-sku
  [product]
  (let [product-c (h/conform! :gcp.big-query/product product {})]
    (common/inline-sub-maps product-c)))



(comment



  (def client (d/client {:server-type        :peer-server
                         :access-key         "myaccesskey"
                         :secret             "mysecret"
                         :endpoint           "localhost:8999"
                         :validate-hostnames false}))

  (def conn (d/connect client {:db-name "workspace"}))

  (def db (d/db conn))

  ;; Query pricing - On demand

  ;; Charges are rounded to the nearest MB, with a minimum 10 MB data
  ;; processed per table referenced by the query, and with a minimum
  ;; 10 MB data processed per query.
  ;; @WARNING: Never forget servicedisplayname when making queries
  (d/q '[:find (pull ?e [*])
         :where
         [?e :gcp.product/service-provider-name "Google"]
         #_[?e :gcp.product/service-regions "us"]
         [?e :gcp.product.category/resource-family "ApplicationServices"]
         [?e :gcp.product.category/resource-group "OnDemandAnalysis"]
         [?e :gcp.product.category/usage-type "OnDemand"]
         [?e :gcp.product.category/service-display-name "BigQuery"]

         [?e :gcp.product/pricing-info ?pi]
         [?pi :gcp.product.pricing-info.pricing-expression/tiered-rates ?tr]
         [?tr :gcp.product.pricing-info.pricing-expression.tiered-rate/unit-price ?price]]
    db)

  ;; Query pricing Slot

  ;; Flat rate per slot, you have to multiply by 500
  (d/q '[:find (pull ?e [*])
         :where
         [?e :gcp.product/description ?d]
         [?e :gcp.product/service-regions "us"]
         [?e :gcp.product.category/service-display-name "BigQuery Reservation API"]
         [?e :gcp.product.category/resource-family "ApplicationServices"]
         [?e :gcp.product.category/resource-group "FlatRate"]
         [?e :gcp.product.category/usage-type "Commit1Mo"]

         [?e :gcp.product/pricing-info ?pi]
         [?pi :gcp.product.pricing-info.pricing-expression/tiered-rates ?tr]
         [?tr :gcp.product.pricing-info.pricing-expression.tiered-rate/unit-price ?price]]

    db)

  ;; Annueal flat rate

  (d/q '[:find (pull ?e [*])
         :where
         [?e :gcp.product/description ?d]
         [?e :gcp.product/service-regions "us"]
         [?e :gcp.product.category/service-display-name "BigQuery Reservation API"]
         [?e :gcp.product.category/resource-family "ApplicationServices"]
         [?e :gcp.product.category/resource-group "FlatRate"]
         [?e :gcp.product.category/usage-type "Commit1Yr"]

         [?e :gcp.product/pricing-info ?pi]
         [?pi :gcp.product.pricing-info.pricing-expression/tiered-rates ?tr]
         [?tr :gcp.product.pricing-info.pricing-expression.tiered-rate/unit-price ?price]]
    db)

  ;; Hourly flat rates
  (d/q '[:find (pull ?e [*])
         :where
         [?e :gcp.product/description ?d]
         [?e :gcp.product/service-regions "us"]
         [?e :gcp.product.category/service-display-name "BigQuery Reservation API"]
         [?e :gcp.product.category/resource-family "ApplicationServices"]
         [?e :gcp.product.category/resource-group "FlatRate"]
         [?e :gcp.product.category/usage-type "OnDemand"]

         [?e :gcp.product/pricing-info ?pi]
         [?pi :gcp.product.pricing-info.pricing-expression/tiered-rates ?tr]
         [?tr :gcp.product.pricing-info.pricing-expression.tiered-rate/unit-price ?price]]
    db)

  )


(comment
  ;; Storage

  ;; Active Storage
  (d/q '[:find (pull ?e [*])
         :where

         [?e :gcp.product/description ?d]
         [?e :gcp.product/service-regions "us"]
         [?e :gcp.product.category/service-display-name "BigQuery"]
         [?e :gcp.product.category/resource-group "ActiveStorage"]
         [?e :gcp.product.category/resource-family "ApplicationServices"]
         [?e :gcp.product.category/usage-type "OnDemand"]

         [?e :gcp.product/pricing-info ?pi]
         [?pi :gcp.product.pricing-info.pricing-expression/tiered-rates ?tr]
         [?tr :gcp.product.pricing-info.pricing-expression.tiered-rate/unit-price ?price]]
    db)


  (d/q '[:find (pull ?e [*])
         :where

         [?e :gcp.product/description ?d]
         [?e :gcp.product/service-regions "us"]
         [?e :gcp.product.category/service-display-name "BigQuery"]
         [?e :gcp.product.category/resource-group "LongTermStorage"]
         [?e :gcp.product.category/resource-family "ApplicationServices"]
         [?e :gcp.product.category/usage-type "OnDemand"]

         [?e :gcp.product/pricing-info ?pi]
         [?pi :gcp.product.pricing-info.pricing-expression/tiered-rates ?tr]
         [?tr :gcp.product.pricing-info.pricing-expression.tiered-rate/unit-price ?price]]

    db)

  )


(comment

  ;; BIG Query Storage API

  ;;per TB read
  (d/q '[:find (pull ?e [*])
         :where

         [?e :gcp.product/description ?d]
         [?e :gcp.product/service-regions "us"]
         [?e :gcp.product.category/service-display-name "BigQuery Storage API"]
         [?e :gcp.product.category/resource-group "Streaming"]
         [?e :gcp.product.category/resource-family "ApplicationServices"]
         [?e :gcp.product.category/usage-type "OnDemand"]

         [?e :gcp.product/pricing-info ?pi]
         [?pi :gcp.product.pricing-info.pricing-expression/tiered-rates ?tr]
         [?tr :gcp.product.pricing-info.pricing-expression.tiered-rate/unit-price ?price]]

    db)

  )

(comment
  ;; Streaming pricing

  (d/q '[:find (pull ?e [*])
         :where

         [?e :gcp.product/description ?d]
         [?e :gcp.product/service-regions "us"]
         [?e :gcp.product.category/service-display-name "BigQuery"]
         [?e :gcp.product.category/resource-group "Streaming"]
         [?e :gcp.product.category/resource-family "ApplicationServices"]
         [?e :gcp.product.category/usage-type "OnDemand"]

         [?e :gcp.product/pricing-info ?pi]
         [?pi :gcp.product.pricing-info.pricing-expression/tiered-rates ?tr]
         [?tr :gcp.product.pricing-info.pricing-expression.tiered-rate/unit-price ?price]]

    db)

  )
