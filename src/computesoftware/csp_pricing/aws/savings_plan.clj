(ns computesoftware.csp-pricing.aws.savings-plan
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [computesoftware.csp-pricing.aws.common :as common]
    [computesoftware.csp-pricing.aws.ec2 :as pricing.ec2]
    [computesoftware.csp-pricing.impl.spec-helpers :as h]))

(s/def :aws.v1.savings-plan/granularity #{"hourly"})
(s/def :aws.v1.savings-plan/instance-type string?)
(s/def :aws.v1.savings-plan/location-type #{"AWS Region" "AWS Wavelength Zone" "AWS Local Zone"})

(def region-name->code
  (-> pricing.ec2/region-name->code
    (assoc "Any" "Any")))

(s/def :aws.v1.savings-plan/attributes
  (h/strictly-conform-json-object2
    attributes-spec
    {"regionCode"     [;; region code, like 'us-east-1'
                       ;; optional, not present in every element
                       :aws.v1.savings-plan/region-code string? :opt]
     "purchaseOption" [:aws.v1.savings-plan/purchase-option
                       {"All Upfront"     :aws.v1.savings-plan.purchase-option/all-upfront
                        "No Upfront"      :aws.v1.savings-plan.purchase-option/no-upfront
                        "Partial Upfront" :aws.v1.savings-plan.purchase-option/partial-upfront}]
     "granularity"    [:aws.v1.savings-plan/granularity :aws.v1.savings-plan/granularity]
     "instanceType"   [:aws.v1.savings-plan/instance-type :aws.v1.savings-plan/instance-type :opt]
     "purchaseTerm"   [:aws.v1.savings-plan/purchase-term
                       {"1yr" :aws.v1.savings-plan.purchase-term/one-year
                        "3yr" :aws.v1.savings-plan.purchase-term/three-year}]
     "locationType"   [:aws.v1.savings-plan/location-type :aws.v1.savings-plan/location-type]
     "location"       [:aws.v1.savings-plan/location region-name->code]}))

(s/def :aws.v1.savings-plan/product-family #{"ComputeSavingsPlans" "EC2InstanceSavingsPlans"})
(s/def :aws.v1.savings-plan/service-code #{"ComputeSavingsPlans"})
(s/def :aws.v1.savings-plan/usage-type string?)
(s/def :aws.v1.savings-plan/usage #{"UnusedBox"
                                    "BoxUsage"
                                    "DedicatedUsage"
                                    "UnusedDed"
                                    "HostUsage"})
(s/def :aws.v1.savings-plan/billing-region string?)
;; this is the instance family, this is an issue with pricing api
(s/def :aws.v1.savings-plan/instance-type string?)

(s/def :aws.v1.savings-plan/pricing
  (h/strictly-conform-json-object2
    rate-spec
    ;; discountedSku are products sku (like an EC2 instance)
    {"discountedSku"         [:aws.v1.savings-plan.pricing/discounted-sku :aws.v1.product/sku]
     "discountedUsageType"   [:aws.v1.savings-plan.pricing/discounted-usage-type string?]
     "discountedOperation"   [:aws.v1.savings-plan.pricing/discounted-operation (conj ec2/usage-operations "")]
     "discountedServiceCode" [:aws.v1.savings-plan.pricing/discounted-service-code
                              #{"AmazonEKS" "AWSLambda" "AmazonEC2" "AmazonECS"}]
     "rateCode"              [:aws.v1.savings-plan.pricing/rate-code
                              #(or (re-matches #"[A-Z0-9]{16}\.[A-Z0-9]{16}" %) ::s/invalid)]
     "unit"                  [:aws.v1.savings-plan.pricing/unit #{"Hrs" "Lambda-GB-Second" "Request"}]
     "discountedRate"        [:aws.v1.savings-plan.pricing/discounted-rate
                              (s/conformer #(try (if (= "USD" (% "currency"))
                                                   (Double/parseDouble (get % "price"))
                                                   ::s/invalid)
                                              (catch java.lang.NumberFormatException _ ::s/invalid)))]}))

(s/def :aws.v1.savings-plan/meta
  (h/strictly-conform-json-object
    {"formatVersion"   [:aws.v1.meta/format-version {"v1.0" "v1.0"}]
     "disclaimer"      [nil string?]
     "version"         [:aws.v1.meta/version string?]
     "publicationDate" [:aws.v1.meta/publication-date (s/conformer common/aws-date-string->date-inst)]
     "regionCode"      [nil string? :opt]}))

(def schema [#:db{:ident       :cs.aws.savings-plan/product-family,
                  :valueType   :db.type/ref
                  :cardinality :db.cardinality/one}
             {:db/ident :cs.aws.savings-plan.product-family/ec2-instance}
             {:db/ident :cs.aws.savings-plan.product-family/compute}

             #:db{:ident       :aws.v1.savings-plan/location-type,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.savings-plan/instance-type,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.savings-plan/granularity,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.savings-plan/purchase-term,
                  :valueType   :db.type/ref
                  :cardinality :db.cardinality/one}
             {:db/ident :aws.v1.savings-plan.purchase-term/one-year}
             {:db/ident :aws.v1.savings-plan.purchase-term/three-year}

             #:db{:ident       :aws.v1.savings-plan/location,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}
             #:db{:ident       :aws.v1.savings-plan/region-code,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.savings-plan/usage-type,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.savings-plan/purchase-option,
                  :valueType   :db.type/ref,
                  :cardinality :db.cardinality/one}
             {:db/ident :aws.v1.savings-plan.purchase-option/all-upfront}
             {:db/ident :aws.v1.savings-plan.purchase-option/no-upfront}
             {:db/ident :aws.v1.savings-plan.purchase-option/partial-upfront}

             #:db{:ident       :aws.v1.savings-plan.pricing/lease-length
                  :valueType   :db.type/long
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.savings-plan/pricing
                  :valueType   :db.type/ref
                  :cardinality :db.cardinality/many
                  :isComponent true}

             #:db{:ident       :aws.v1.savings-plan.pricing/discounted-usage-type,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.savings-plan.pricing/usage
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.savings-plan.pricing/billing-region
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.savings-plan.pricing/instance-type
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.savings-plan.pricing/discounted-service-code,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.savings-plan.pricing/discounted-operation,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.savings-plan.pricing/discounted-rate,
                  :valueType   :db.type/double,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.savings-plan.pricing/discounted-sku,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.savings-plan.pricing/unit,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.savings-plan.pricing/rate-code,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}])

(defn product-entity-map
  [product]
  (let [{:strs [sku productFamily serviceCode serviceCode usageType attributes]} product
        sku (h/conform! :aws.v1.product/sku sku {:product product})
        product-family (h/conform! :aws.v1.savings-plan/product-family productFamily {:product product})
        usage-type (h/conform! :aws.v1.savings-plan/usage-type usageType {:product product})
        attributes (h/conform! :aws.v1.savings-plan/attributes attributes {:product product})
        service-code (h/conform! :aws.v1.savings-plan/service-code serviceCode {:product product})]
    (merge {:aws.v1.product/sku             sku
            :aws.v1.product/product-family  product-family
            :aws.v1.product/service-code    service-code
            :aws.v1.savings-plan/usage-type usage-type}
      (assoc attributes
        :cs.aws.savings-plan/product-family
        (case productFamily
          "ComputeSavingsPlans" :cs.aws.savings-plan.product-family/compute
          "EC2InstanceSavingsPlans" :cs.aws.savings-plan.product-family/ec2-instance)))))

(def usage-type-re #"(\w+)-?(\w+)?")

(defn parse-discounted-usage-type
  [usage-type]
  (let [[usage maybe-instance-type] (str/split usage-type #":" 2)
        [_ & match] (re-find usage-type-re usage)]
    (conj
      (case (count (filter some? match))
        1 ["USE1" (first match)]
        2 (vec match))
      maybe-instance-type)))

(comment
  (map parse-discounted-usage-type ["BoxUsage:d3en.xlarge" "USW2-BoxUsage:c5n.18xlarge" "APN1-DedicatedUsage" "BoxUsage:u-6tb1.112xlarge"]))

(defn prices-entity-map [term prices]
  (let [{:strs [sku effectiveDate leaseContractLength rates]} prices
        sku (h/conform! :aws.v1.product/sku sku {:price (dissoc prices "rates")})
        lease-contract-length (h/conform! (s/conformer #(if (= "year" (% "unit"))
                                                          (s/conform int? (% "duration"))
                                                          ::s/invalid))
                                leaseContractLength
                                {:price (dissoc prices "rates")})
        effective-date (h/conform! :aws.v1.pricing/effective-date effectiveDate {:price (dissoc prices "rates")})

        raw-rate->augmented-rate (fn [raw-rate]
                                   (let [[billing-region usage maybe-instance-type]
                                         (h/conform!
                                           (s/tuple string? string? (s/nilable string?))
                                           (parse-discounted-usage-type (:aws.v1.savings-plan.pricing/discounted-usage-type raw-rate))
                                           {:rate raw-rate})]
                                     (cond-> (assoc raw-rate
                                               :aws.v1.savings-plan.pricing/billing-region billing-region
                                               :aws.v1.savings-plan.pricing/usage usage)
                                       maybe-instance-type
                                       (assoc :aws.v1.savings-plan.pricing/instance-type maybe-instance-type))))
        rates
        (into [] (map #(->> (h/conform! :aws.v1.savings-plan/pricing % {:rate %})
                         raw-rate->augmented-rate)) rates)]
    ;; savingsPlan is conformed to make sure this is the only possible key that we can encounter.
    (h/conform! #(= % "savingsPlan") term {:price (dissoc prices "rates")})
    (conj (mapv (fn [rate]
                  {:aws.v1.product/sku          sku
                   :aws.v1.savings-plan/pricing [rate]}) rates)
      {:aws.v1.product/sku                       sku
       :aws.v1.savings-plan.pricing/lease-length lease-contract-length
       :aws.v1.pricing/effective-date            effective-date})))
