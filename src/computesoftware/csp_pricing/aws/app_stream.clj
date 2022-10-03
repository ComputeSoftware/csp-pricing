(ns computesoftware.csp-pricing.aws.app-stream
  (:require
    [clojure.spec.alpha :as s]
    [kwill.anomkit :as ak]
    [computesoftware.csp-pricing.aws.common]
    [computesoftware.csp-pricing.impl.spec-helpers :as h]
    [datomic.client.api :as d]))


(s/def :aws.v1.appstream.product/vcpu #{"1" "4" "8" "48" "12" "24" "32" "2" "16" "64"})

(s/def :aws.v1.appstream.product/video-memory-gib #{"N/A" "4" "8" "1" "32" "2" "16" "64"})

(s/def :aws.v1.appstream.product/instance-function #{"Fleet" "StoppedFleetInstance" "ImageBuilder"})

(s/def :aws.v1.appstream.product/servicename #{"Amazon AppStream"})

(s/def :aws.v1.appstream.product/servicecode #{"AmazonAppStream"})

(s/def :aws.v1.appstream.product/os-license-model #{"License Included"})

(s/def :aws.v1.appstream.product/operation #{"Streaming:001" "StoppedInstance" "RDS CAL" "AppStreamImageStorge"})

(s/def :aws.v1.appstream.product/location-type #{"AWS Region"})

(s/def :aws.v1.appstream.product/usage-type string?)

(s/def :aws.v1.appstream.product/operating-system #{"Windows"})

(s/def :aws.v1.appstream.product/memory-gib
  #{"2"
    "3.75"
    "4"
    "7.5"
    "8"
    "15"
    "15.3"
    "16"
    "30"
    "30.5"
    "32"
    "60"
    "61"
    "64"
    "96"
    "122"
    "128"
    "192"
    "244"
    "256"
    "384"
    "488"})

(s/def :aws.v1.appstream.product/instance-type string?)

(s/def :aws.v1.appstream.product/license-model #{"Bring Your Own License" "AppStream provided"})

(s/def :aws.v1.appstream.product/instance-family #{"Graphics Design" "Graphics Pro" "Graphics Desktop" "Graphics"
                                                   "Compute optimized" "Memory optimized" "General purpose"})

(s/def :aws.v1.appstream.product/attributes
  (h/strictly-conform-json-object2
    attributes-spec
    {"vcpu"             [:aws.v1.appstream.product/vcpu :aws.v1.appstream.product/vcpu :opt]
     "videoMemoryGib"   [:aws.v1.appstream.product/video-memory-gib :aws.v1.appstream.product/video-memory-gib :opt]
     "instanceFunction" [:aws.v1.appstream.product/instance-function :aws.v1.appstream.product/instance-function :opt]
     "servicename"      [:aws.v1.appstream.product/servicename :aws.v1.appstream.product/servicename]
     "servicecode"      [:aws.v1.appstream.product/servicecode :aws.v1.appstream.product/servicecode]
     "osLicenseModel"   [:aws.v1.appstream.product/os-license-model :aws.v1.appstream.product/os-license-model :opt]
     "location"         [:aws.v1.appstream.product/location
                         {"AWS GovCloud (US-West)"   "us-gov-west-1",
                          "Any"                      "any",
                          "Asia Pacific (Mumbai)"    "ap-south-1",
                          "Asia Pacific (Seoul)"     "ap-northeast-2",
                          "Asia Pacific (Singapore)" "ap-southeast-1",
                          "Asia Pacific (Sydney)"    "ap-southeast-2",
                          "Asia Pacific (Tokyo)"     "ap-northeast-1",
                          "China (Ningxia)"          "cn-northwest-1"
                          "EU (Frankfurt)"           "eu-central-1",
                          "EU (Ireland)"             "eu-west-1",
                          "US East (N. Virginia)"    "us-east-1",
                          "US West (Oregon)"         "us-west-2"}]
     "operation"        [:aws.v1.appstream.product/operation :aws.v1.appstream.product/operation]
     "locationType"     [:aws.v1.appstream.product/location-type :aws.v1.appstream.product/location-type]
     "usagetype"        [:aws.v1.appstream.product/usage-type :aws.v1.appstream.product/usage-type]
     "operatingSystem"  [:aws.v1.appstream.product/operating-system :aws.v1.appstream.product/operating-system :opt]
     "memoryGib"        [:aws.v1.appstream.product/memory-gib :aws.v1.appstream.product/memory-gib :opt]
     "instanceType"     [:aws.v1.appstream.product/instance-type :aws.v1.appstream.product/instance-type :opt]
     "licenseModel"     [:aws.v1.appstream.product/license-model :aws.v1.appstream.product/license-model :opt]
     "instanceFamily"   [:aws.v1.appstream.product/instance-family :aws.v1.appstream.product/instance-family :opt]}))

(s/def :aws.v1.appstream.pricing/price-dimensions
  (h/strictly-conform-json-object2
    price-dimensions-spec
    {"rateCode"     [:aws.v1.pricing/rate-code string?]
     "description"  [:aws.v1.pricing/description string?]
     "beginRange"   [nil string? :opt]
     "endRange"     [nil string? :opt]
     "unit"         [:aws.v1.pricing/unit (s/conformer #(case %
                                                          "hour" :aws.v1.pricing.unit/hour
                                                          "Month" :aws.v1.pricing.unit/month
                                                          "GiB" :aws.v1.pricing.unit/gb
                                                          ("user" "users") :aws.v1.pricing.unit/user
                                                          ::s/invalid))]
     "pricePerUnit" [:aws.v1.pricing.price-per-unit/value
                     (s/conformer #(try (Double/parseDouble (get % "USD"))
                                     (catch java.lang.NumberFormatException _ ::s/invalid)))]
     "appliesTo"    [nil (s/coll-of string?)]}))

(def schema [#:db{:ident       :aws.v1.appstream.product/vcpu
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.appstream.product/video-memory-gib
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.appstream.product/instance-function
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.appstream.product/servicename
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.appstream.product/servicecode
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.appstream.product/os-license-model
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.appstream.product/location
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.appstream.product/operation
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.appstream.product/location-type
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.appstream.product/usage-type
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.appstream.product/operating-system
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.appstream.product/memory-gib
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.appstream.product/instance-type
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.appstream.product/license-model
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.appstream.product/instance-family
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}])

(defn product-entity-map
  [sku product]
  (let [{:strs [sku productFamily attributes]} product
        sku (h/conform! :aws.v1.product/sku sku {:product product})
        product-family (h/conform! #{"Image Fees"
                                     "Stopped Instance"
                                     "Streaming Instance"
                                     "User Fees"} productFamily {:product product})
        attributes (h/conform! :aws.v1.appstream.product/attributes attributes {:product product})]
    (merge {:aws.v1.product/sku            sku
            :aws.v1.product/product-family product-family}
      attributes)))

(defn price-entity-map [term sku offer price]
  (let [{:strs [offerTermCode sku effectiveDate priceDimensions]} price
        sku (h/conform! :aws.v1.product/sku sku {:price price})
        offer-term-code (h/conform! :aws.v1.pricing/offer-term-code offerTermCode {:price price})
        effective-date (h/conform! :aws.v1.pricing/effective-date effectiveDate {:price price})
        price-dimensions (into [] (map #(h/conform! :aws.v1.appstream.pricing/price-dimensions % {:price price})) (vals priceDimensions))]
    (let [term-key (case term "OnDemand" :aws.v1.term/on-demand "Reserved" :aws.v1.term/reserved)]
      {:aws.v1.product/sku sku
       term-key            {:aws.v1.pricing/offer-term-code offer-term-code
                            :aws.v1.pricing/effective-date  effective-date
                            :aws.v1.pricing/dimensions      price-dimensions}})))

(defn -resource-price-per-hour*
  [db {:resource-price.aws-appstream/keys [instance-type region]}]
  (let [aws-region region
        [f :as price-list-items]
        (d/q '[:find ?price-val
               :in $ ?aws-region ?aws-instance-type
               :where
               [?e :aws.v1.appstream.product/location ?aws-region]
               [?e :aws.v1.appstream.product/instance-function "Fleet"]
               [?e :aws.v1.appstream.product/instance-type ?aws-instance-type]
               [?e :aws.v1.term/on-demand ?t]
               [?t :aws.v1.pricing/dimensions ?d]
               [?d :aws.v1.pricing/unit :aws.v1.pricing.unit/hour]
               [?d :aws.v1.pricing.price-per-unit/value ?price-val]]
          db aws-region instance-type)]
    (case (bounded-count 2 price-list-items)
      0 (ak/not-found "No matching prices found.")
      1 (first f)
      (ak/incorrect (format "Too many resource prices found (count: %s)" (count price-list-items))
        {:prices price-list-items}))))



(comment


  (def client (d/client {:server-type        :peer-server
                         :access-key         "myaccesskey"
                         :secret             "mysecret"
                         :endpoint           "localhost:8999"
                         :validate-hostnames false}))

  (def conn (d/connect client {:db-name "workspace"}))

  (def db (d/db conn))

  (-resource-price-per-hour*
    db
    {:resource-price/provider                    :resource-price.provider/app-stream
     :resource-price.aws-appstream/region        "us-east-1"
     :resource-price.aws-appstream/instance-type "stream.graphics-pro.4xlarge"})
  2.05

  (-resource-price-per-hour*
    db
    {:resource-price/provider                    :resource-price.provider/app-stream
     :resource-price.aws-appstream/region        "us-east-1"
     :resource-price.aws-appstream/instance-type "stream.graphics-design.large"})

  0.25

  )
