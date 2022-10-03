(ns computesoftware.csp-pricing.gcp.common)

(def schema
  [#:db{:ident       :gcp.product/name,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one
        :db/unique   :db.unique/identity}

   #:db{:ident       :gcp.product/sku-id,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one
        :db/unique   :db.unique/identity}

   #:db{:ident       :gcp.product/description,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #_#:db{:ident       :gcp.product/category,
          :valueType   :db.type/ref,
          :cardinality :db.cardinality/one,
          :isComponent true}

   #:db{:ident       :gcp.product.category/service-display-name,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #:db{:ident       :gcp.product.category/resource-family,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #:db{:ident       :gcp.product.category/resource-group,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #:db{:ident       :gcp.product.category/usage-type,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #:db{:ident       :gcp.product/service-regions,
        :valueType   :db.type/string
        :cardinality :db.cardinality/many,}

   #:db{:ident       :gcp.product/pricing-info,
        :valueType   :db.type/ref,
        :cardinality :db.cardinality/many,
        :isComponent true}

   #:db{:ident       :gcp.product.pricing-info/summary,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #_#:db{:ident       :gcp.product.pricing-info/pricing-expression,
          :valueType   :db.type/ref,
          :cardinality :db.cardinality/one
          :isComponent true}

   #:db{:ident
        :gcp.product.pricing-info.pricing-expression/usage-unit,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #:db{:ident
        :gcp.product.pricing-info.pricing-expression/usage-unit-description,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #:db{:ident
        :gcp.product.pricing-info.pricing-expression/base-unit,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #:db{:ident
        :gcp.product.pricing-info.pricing-expression/base-unit-description,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #:db{:ident
        :gcp.product.pricing-info.pricing-expression/base-unit-conversion-factor,
        :valueType   :db.type/double
        :cardinality :db.cardinality/one}

   #:db{:ident
        :gcp.product.pricing-info.pricing-expression/display-quantity,
        :valueType   :db.type/long
        :cardinality :db.cardinality/one}

   #:db{:ident
        :gcp.product.pricing-info.pricing-expression/tiered-rates,
        :valueType   :db.type/ref,
        :cardinality :db.cardinality/many,
        :isComponent true}

   #:db{:ident
        :gcp.product.pricing-info.pricing-expression.tiered-rate/start-usage-amount,
        :valueType   :db.type/long
        :cardinality :db.cardinality/one}

   #_#:db{:ident
          :gcp.product.pricing-info.pricing-expression.tiered-rates/unit-price,
          :valueType   :db.type/ref,
          :cardinality :db.cardinality/one
          :isComponent true}

   #:db{:ident
        :gcp.product.pricing-info.pricing-expression.tiered-rate/unit-price,
        :valueType   :db.type/double,
        :cardinality :db.cardinality/one}

   #:db{:ident
        :gcp.product.pricing-info.pricing-expression.tiered-rate.unit-price/currency-code,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #:db{:ident
        :gcp.product.pricing-info.pricing-expression.tiered-rate.unit-price/units,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #:db{:ident
        :gcp.product.pricing-info.pricing-expression.tiered-rate.unit-price/nanos,
        :valueType   :db.type/long
        :cardinality :db.cardinality/one}

   #:db{:ident
        :gcp.product.pricing-info/currency-conversion-rate,
        :valueType   :db.type/long
        :cardinality :db.cardinality/one}

   #:db{:ident       :gcp.product.pricing-info/effective-time,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}

   #:db{:ident       :gcp.product/service-provider-name,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}])

(defn- inline [m k]
  (-> m (dissoc k) (into (m k))))

(defn- for-each [xs f & args]
  (map #(apply f % args) xs))

(defn inline-sub-maps [product]
  (-> product
    (inline :gcp.product/category)
    (update :gcp.product/pricing-info
      for-each
      (fn [pricing-info]
        (-> pricing-info
          (inline :gcp.product.pricing-info/pricing-expression)
          (update :gcp.product.pricing-info.pricing-expression/tiered-rates
            for-each
            (fn [tiered-rate]
              (let [{:gcp.product.pricing-info.pricing-expression.tiered-rate.unit-price/keys [units nanos]
                     :as                                                                      tiered-rate}
                    (inline tiered-rate :gcp.product.pricing-info.pricing-expression.tiered-rate/unit-price)]
                (assoc tiered-rate :gcp.product.pricing-info.pricing-expression.tiered-rate/unit-price
                  (+ (Long/parseLong units) (* 1e-9 nanos)))))))))))
