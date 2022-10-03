(ns computesoftware.csp-pricing.aws.ecs-fargate
  (:require
    [clojure.spec.alpha :as s]
    [computesoftware.csp-pricing.aws.common]
    [computesoftware.csp-pricing.impl.spec-helpers :as h]
    [datomic.client.api :as d]))

(s/def :aws.v1.ecs-fargate.product/location-type #{"AWS Region"})

(s/def :aws.v1.ecs-fargate.product/operation #{""})

(s/def :aws.v1.ecs-fargate.product/service-code #{"AmazonECS"})

(s/def :aws.v1.ecs-fargate.product/service-name #{"Amazon EC2 Container Service"})

(s/def :aws.v1.ecs-fargate.product/usage-type string?)

(s/def :aws.v1.ecs-fargate.product/storage-type #{"default"})

(s/def :aws.v1.ecs-fargate.product/attributes-fargate
  (h/strictly-conform-json-object2
    fargate-attributes-spec
    {"cputype"      [:aws.v1.ecs-fargate.product/cpu-type {"perCPU" :aws.v1.ecs-fargate.product.cpu-type/per-cpu} :opt]
     "location"     [:aws.v1.ecs-fargate.product/location {"AWS GovCloud (US-East)"    "us-gov-east-1"
                                                           "AWS GovCloud (US-West)"    "us-gov-west-1",
                                                           "Africa (Cape Town)"        "af-south-1",
                                                           "Asia Pacific (Hong Kong)"  "ap-east-1",
                                                           "Asia Pacific (Mumbai)"     "ap-south-1",
                                                           "Asia Pacific (Osaka)"      "ap-northeast-3"
                                                           "Asia Pacific (Seoul)"      "ap-northeast-2",
                                                           "Asia Pacific (Singapore)"  "ap-southeast-1",
                                                           "Asia Pacific (Sydney)"     "ap-southeast-2",
                                                           "Asia Pacific (Tokyo)"      "ap-northeast-1",
                                                           "Canada (Central)"          "ca-central-1",
                                                           "EU (Frankfurt)"            "eu-central-1",
                                                           "EU (Ireland)"              "eu-west-1",
                                                           "EU (London)"               "eu-west-2",
                                                           "EU (Milan)"                "eu-south-1",
                                                           "EU (Paris)"                "eu-west-3",
                                                           "EU (Stockholm)"            "eu-north-1",
                                                           "Middle East (Bahrain)"     "me-south-1",
                                                           "South America (Sao Paulo)" "sa-east-1",
                                                           "US East (N. Virginia)"     "us-east-1",
                                                           "US East (Ohio)"            "us-east-2",
                                                           "US West (N. California)"   "us-west-1",
                                                           "US West (Oregon)"          "us-west-2",}]
     "locationType" [:aws.v1.ecs-fargate.product/location-type :aws.v1.ecs-fargate.product/location-type]
     "memorytype"   [:aws.v1.ecs-fargate.product/memory-type {"perGB" :aws.v1.ecs-fargate.product.memory-type/per-gb} :opt]
     "operation"    [nil :aws.v1.ecs-fargate.product/operation]
     "servicecode"  [:aws.v1.ecs-fargate.product/service-code :aws.v1.ecs-fargate.product/service-code]
     "servicename"  [:aws.v1.ecs-fargate.product/service-name :aws.v1.ecs-fargate.product/service-name]
     "tenancy"      [:aws.v1.ecs-fargate.product/tenancy {"Shared" :aws.v1.ecs-fargate.product.tenancy/shared} :opt]
     "usagetype"    [:aws.v1.ecs-fargate.product/usage-type :aws.v1.ecs-fargate.product/usage-type]
     "storagetype"  [:aws.v1.ecs-fargate.product/storage-type :aws.v1.ecs-fargate.product/storage-type :opt]}))

(s/def :aws.v1.ecs-fargate.product/attributes-ecs
  (h/strictly-conform-json-object2
    ecs-attributes-spec
    {"cputype"      [:aws.v1.ecs-fargate.product/cpu-type {"perCPU" :aws.v1.ecs-fargate.product.cpu-type/per-cpu} :opt]
     "location"     [:aws.v1.ecs-fargate.product/location {"EU (Ireland)"              "eu-west-1",
                                                           "AWS GovCloud (US-West)"    "us-gov-west-1",
                                                           "EU (London)"               "eu-west-2",
                                                           "EU (Frankfurt)"            "eu-central-1",
                                                           "Asia Pacific (Sydney)"     "ap-southeast-2",
                                                           "US West (N. California)"   "us-west-1",
                                                           "Asia Pacific (Singapore)"  "ap-southeast-1",
                                                           "Canada (Central)"          "ca-central-1",
                                                           "South America (Sao Paulo)" "sa-east-1",
                                                           "Asia Pacific (Tokyo)"      "ap-northeast-1",
                                                           "Asia Pacific (Hong Kong)"  "ap-east-1",
                                                           "Asia Pacific (Seoul)"      "ap-northeast-2",
                                                           "Middle East (Bahrain)"     "me-south-1",
                                                           "Africa (Cape Town)"        "af-south-1",
                                                           "US West (Oregon)"          "us-west-2",
                                                           "US East (N. Virginia)"     "us-east-1",
                                                           "Asia Pacific (Mumbai)"     "ap-south-1",
                                                           "EU (Stockholm)"            "eu-north-1",
                                                           "EU (Paris)"                "eu-west-3",
                                                           "EU (Milan)"                "eu-south-1",
                                                           "US East (Ohio)"            "us-east-2",
                                                           "AWS GovCloud (US-East)"    "us-gov-east-1"}]
     "locationType" [:aws.v1.ecs-fargate.product/location-type :aws.v1.ecs-fargate.product/location-type]
     "memorytype"   [:aws.v1.ecs-fargate.product/memory-type {"perGB" :aws.v1.ecs-fargate.product.memory-type/per-gb} :opt]
     "operation"    [nil :aws.v1.ecs-fargate.product/operation]
     "servicecode"  [:aws.v1.ecs-fargate.product/service-code :aws.v1.ecs-fargate.product/service-code]
     "servicename"  [:aws.v1.ecs-fargate.product/service-name :aws.v1.ecs-fargate.product/service-name]
     "usagetype"    [:aws.v1.ecs-fargate.product/usage-type :aws.v1.ecs-fargate.product/usage-type]}))

(s/def :aws.v1.ecs-fargate.pricing/price-dimensions
  (h/strictly-conform-json-object2
    price-dimensions-spec
    {"rateCode"     [:aws.v1.pricing/rate-code string?]
     "description"  [:aws.v1.pricing/description string?]
     "beginRange"   [nil #{"0"}]
     "endRange"     [nil #{"Inf"}]
     "unit"         [:aws.v1.pricing/unit (s/conformer #(case %
                                                          "hours" :aws.v1.pricing.unit/hour
                                                          "GB-Hours" :aws.v1.pricing.unit/gb-hour
                                                          "vCPU-Hours" :aws.v1.pricing.unit/vcpu-hour
                                                          ::s/invalid))]
     "pricePerUnit" [:aws.v1.pricing.price-per-unit/value
                     (s/conformer #(try (Double/parseDouble (get % "USD"))
                                     (catch java.lang.NumberFormatException _ ::s/invalid)))]
     "appliesTo"    [nil (s/coll-of string?)]}))

(def schema [#:db{:ident       :cs.aws.ecs-fargate/product-family
                  :valueType   :db.type/ref
                  :cardinality :db.cardinality/one}
             {:db/ident :cs.aws.ecs-fargate.product-family/fargate}
             {:db/ident :cs.aws.ecs-fargate.product-family/ecs}

             #:db{:ident       :aws.v1.ecs-fargate.product/service-name,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.ecs-fargate.product/service-code,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.ecs-fargate.product/cpu-type,
                  :valueType   :db.type/ref,
                  :cardinality :db.cardinality/one}
             {:db/ident :aws.v1.ecs-fargate.product.cpu-type/per-cpu}

             #:db{:ident       :aws.v1.ecs-fargate.product/tenancy,
                  :valueType   :db.type/ref,
                  :cardinality :db.cardinality/one}
             {:db/ident :aws.v1.ecs-fargate.product.tenancy/shared}

             #:db{:ident       :aws.v1.ecs-fargate.product/location,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.ecs-fargate.product/memory-type,
                  :valueType   :db.type/ref
                  :cardinality :db.cardinality/one}
             {:db/ident :aws.v1.ecs-fargate.product.memory-type/per-gb}

             #:db{:ident       :aws.v1.ecs-fargate.product/location-type,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.ecs-fargate.product/usage-type,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.ecs-fargate.product/storage-type,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}])

(defn product-entity-map
  [sku product]
  (let [{:strs [sku productFamily attributes]} product
        sku (h/conform! :aws.v1.product/sku sku {:product product})
        product-family (h/conform! #{"Compute Metering" "Compute"} productFamily {:product product})
        attributes (h/conform! (case productFamily
                                 "Compute" :aws.v1.ecs-fargate.product/attributes-fargate
                                 "Compute Metering" :aws.v1.ecs-fargate.product/attributes-ecs)
                     attributes {:product product})]
    (merge {:aws.v1.product/sku            sku
            :aws.v1.product/product-family product-family}
      (assoc attributes
        :cs.aws.ecs-fargate/product-family
        (case productFamily
          "Compute" :cs.aws.ecs-fargate.product-family/fargate
          "Compute Metering" :cs.aws.ecs-fargate.product-family/ecs)))))

(defn price-entity-map [term sku offer price]
  (let [{:strs [offerTermCode sku effectiveDate priceDimensions]} price
        sku (h/conform! :aws.v1.product/sku sku {:price price})
        offer-term-code (h/conform! :aws.v1.pricing/offer-term-code offerTermCode {:price price})
        effective-date (h/conform! :aws.v1.pricing/effective-date effectiveDate {:price price})
        price-dimensions (into [] (map #(h/conform! :aws.v1.ecs-fargate.pricing/price-dimensions % {:price price})) (vals priceDimensions))]
    (let [term-key (case term "OnDemand" :aws.v1.term/on-demand)]
      {:aws.v1.product/sku sku
       term-key            {:aws.v1.pricing/offer-term-code offer-term-code
                            :aws.v1.pricing/effective-date  effective-date
                            :aws.v1.pricing/dimensions      price-dimensions}})))

(comment
  (qbe/q
    '[:find ?base-price (pull ?id [*])
      :where
      #:aws.v1.ecs-fargate.product
              {:db/id    ?id                                ; new feature to retrieve the entity id
               :aws.v1.term/on-demand
               {:aws.v1.pricing/dimensions
                {:aws.v1.pricing.price-per-unit/value ?base-price}}
               :location "eu-west-3"}]
    (d/db conn)))
