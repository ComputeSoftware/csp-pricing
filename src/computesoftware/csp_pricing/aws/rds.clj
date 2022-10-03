(ns computesoftware.csp-pricing.aws.rds
  (:require
    [clojure.spec.alpha :as s]
    [computesoftware.csp-pricing.aws.common]
    [computesoftware.csp-pricing.impl.spec-helpers :as h]
    [datomic.client.api :as d]))

(s/def :aws.v1.rds.product/database-edition
  #{"Any"
    "Enterprise"
    "Express"
    "Standard"
    "Standard One"
    "Standard Two"
    "Web"})

(s/def :aws.v1.rds.product/min-volume-size #{"100 GB" "5 GB" "10 GB"})

(s/def :aws.v1.rds.product/vcpu #{"0" "4" "8" "96" "128" "48" "36" "12" "24" "1" "32" "2" "72" "16" "40" "64"})

(s/def :aws.v1.rds.product/physical-processor string?)

(s/def :aws.v1.rds.product/group
  #{"API Request"
    "Aurora Backtrack"
    "Aurora Global Database"
    "Aurora I/O Operation"
    "Provisioned GB"
    "Provisioned IO/s"
    "RDS I/O Operation"
    "RDS Snapshot Export"
    "RDS-PIOPS"})

(s/def :aws.v1.rds.product/servicename #{"Amazon Relational Database Service"})

(s/def :aws.v1.rds.product/servicecode #{"AmazonRDS"})

(s/def :aws.v1.rds.product/max-volume-size #{"16 TB" "64 TB" "3 TB"})

(s/def :aws.v1.rds.product/deployment-option
  #{"Single-AZ"
    "Multi-AZ"
    "Multi-AZ (readable standbys)"
    "Multi-AZ (SQL Server Mirror)"})

(s/def :aws.v1.rds.product/group-description #{"RDS Provisioned IOPS" "Aurora Global Database Replicated IO"
                                               "Input/Output Operation" "Aurora Backtrack Change Records"
                                               "RDS Snapshot Size" "Performance Insights API requests"})

(s/def :aws.v1.rds.product/network-performance string?)

(s/def :aws.v1.rds.product/operation string?)

(s/def :aws.v1.rds.product/location-type #{"AWS Region" "AWS Outposts"})

(s/def :aws.v1.rds.product/memory string?)

(s/def :aws.v1.rds.product/volume-type
  #{"General Purpose"
    "General Purpose (SSD)"
    "General Purpose-Aurora"
    "Magnetic"
    "Provisioned IOPS"
    "Provisioned IOPS (SSD)"})

(s/def :aws.v1.rds.product/volume-name
  #{"gp2" "io1"})

(s/def :aws.v1.rds.product/database-engine
  #{"Any"
    "Aurora MySQL"
    "Aurora PostgreSQL"
    "MariaDB"
    "MySQL"
    "MySQL (on-premise for Outpost)"
    "Oracle"
    "PostgreSQL"
    "PostgreSQL (on-premise for Outpost)"
    "PostgreSQL (on-premise for Outposts)"
    "SQL Server"
    "SQL Server (on-premise for Outpost)"})

(s/def :aws.v1.rds.product/license-model
  #{"Bring your own license"
    "License included"
    "NA"
    "No license required"
    "On-premises customer provided license"})

(s/def :aws.v1.rds.product/engine-media-type
  #{"AWS-provided"
    "Customer-provided"})

(s/def :aws.v1.rds.product/deployment-model
  #{"Custom"})

(s/def :aws.v1.rds.product/acu
  #{"1"})

(s/def :aws.v1.rds.product/instance-type-family
  #{"CV11"
    "M1"
    "M2"
    "M3"
    "M4"
    "M5"
    "M5d"
    "M6i"
    "M6G"
    "M6GD"
    "MV11"
    "ProvisionedAMR"
    "ProvisionedFMR"
    "R3"
    "R4"
    "R5"
    "R5b"
    "R5d"
    "R6G"
    "R6GD"
    "R6i"
    "RV11"
    "ServerlessAMR"
    "ServerlessFMR"
    "T1"
    "T2"
    "T3"
    "T4G"
    "X1"
    "X1e"
    "X2G"
    "Z1D"})

(s/def :aws.v1.rds.product/instance-family
  #{"Compute optimized"
    "General purpose"
    "Memory optimized"
    "Micro instances"
    "T3"
    "T4G"})

(s/def :aws.v1.rds.product/attributes
  (h/strictly-conform-json-object2
    attributes-spec
    {"databaseEdition"             [:aws.v1.rds.product/database-edition :aws.v1.rds.product/database-edition :opt]
     "clockSpeed"                  [:aws.v1.rds.product/clock-speed string? :opt]
     "processorArchitecture"       [:aws.v1.rds.product/processor-architecture string? :opt]
     "processorFeatures"           [:aws.v1.rds.product/processor-features string? :opt]
     "minVolumeSize"               [:aws.v1.rds.product/min-volume-size :aws.v1.rds.product/min-volume-size :opt]
     "vcpu"                        [:aws.v1.rds.product/vcpu :aws.v1.rds.product/vcpu :opt]
     "physicalProcessor"           [:aws.v1.rds.product/physical-processor :aws.v1.rds.product/physical-processor :opt]
     "group"                       [:aws.v1.rds.product/group :aws.v1.rds.product/group :opt]
     "servicename"                 [:aws.v1.rds.product/servicename :aws.v1.rds.product/servicename]
     "servicecode"                 [:aws.v1.rds.product/servicecode :aws.v1.rds.product/servicecode]
     "maxVolumeSize"               [:aws.v1.rds.product/max-volume-size :aws.v1.rds.product/max-volume-size :opt]
     "deploymentOption"            [:aws.v1.rds.product/deployment-option :aws.v1.rds.product/deployment-option :opt]
     "storage"                     [:aws.v1.rds.product/storage string? :opt]
     "groupDescription"            [:aws.v1.rds.product/group-description :aws.v1.rds.product/group-description :opt]
     "engineCode"                  [:aws.v1.rds.product/engine-code #"\d+" :opt]
     "normalizationSizeFactor"     [:aws.v1.rds.product/normalization-size-factor string? :opt]
     "enhancedNetworkingSupported" [:aws.v1.rds.product/enhanced-networking-supported #{"Yes"} :opt]
     "storageMedia"                [:aws.v1.rds.product/storage-media #{"SSD" "Magnetic" "AmazonS3"} :opt]
     "location"                    [:aws.v1.rds.product/location {"AWS GovCloud (US-East)"     "us-gov-east-1"
                                                                  "AWS GovCloud (US-West)"     "us-gov-west-1"
                                                                  "Africa (Cape Town)"         "Africa (Cape Town)"
                                                                  "Any"                        "any"
                                                                  "Asia Pacific (Hong Kong)"   "ap-east-1"
                                                                  "Asia Pacific (Jakarta)"     "ap-southeast-3"
                                                                  "Asia Pacific (Mumbai)"      "ap-south-1"
                                                                  "Asia Pacific (Osaka)"       "ap-northeast-3"
                                                                  "Asia Pacific (Osaka-Local)" "ap-northeast-3"
                                                                  "Asia Pacific (Seoul)"       "ap-northeast-2"
                                                                  "Asia Pacific (Singapore)"   "ap-southeast-1"
                                                                  "Asia Pacific (Sydney)"      "ap-southeast-2"
                                                                  "Asia Pacific (Tokyo)"       "ap-northeast-1"
                                                                  "Canada (Central)"           "ca-central-1"
                                                                  "EU (Frankfurt)"             "eu-central-1"
                                                                  "EU (Ireland)"               "eu-west-1"
                                                                  "EU (London)"                "eu-west-2"
                                                                  "EU (Milan)"                 "EU (Milan)"
                                                                  "EU (Paris)"                 "eu-west-3"
                                                                  "EU (Stockholm)"             "eu-north-1"
                                                                  "Middle East (Bahrain)"      "me-south-1"
                                                                  "South America (Sao Paulo)"  "sa-east-1"
                                                                  "US East (N. Virginia)"      "us-east-1"
                                                                  "US East (Ohio)"             "us-east-2"
                                                                  "US West (Los Angeles)"      "US West (Los Angeles)"
                                                                  "US West (N. California)"    "us-west-1"
                                                                  "US West (Oregon)"           "us-west-2"}]
     "regionCode"                  [nil string?]
     "networkPerformance"          [:aws.v1.rds.product/network-performance :aws.v1.rds.product/network-performance :opt]
     "operation"                   [:aws.v1.rds.product/operation :aws.v1.rds.product/operation]
     "locationType"                [:aws.v1.rds.product/location-type :aws.v1.rds.product/location-type]
     "memory"                      [:aws.v1.rds.product/memory :aws.v1.rds.product/memory :opt]
     "volumeType"                  [:aws.v1.rds.product/volume-type :aws.v1.rds.product/volume-type :opt]
     "volumeName"                  [:aws.v1.rds.product/volume-name :aws.v1.rds.product/volume-name :opt]
     "usagetype"                   [:aws.v1.rds.product/usage-type string?]
     "instanceType"                [:aws.v1.rds.product/instance-type string? :opt]
     "databaseEngine"              [:aws.v1.rds.product/database-engine :aws.v1.rds.product/database-engine :opt]
     "licenseModel"                [:aws.v1.rds.product/license-model :aws.v1.rds.product/license-model :opt]
     "currentGeneration"           [:aws.v1.rds.product/current-generation #{"Yes" "No"} :opt]
     "instanceTypeFamily"          [:aws.v1.rds.product/instance-type-family :aws.v1.rds.product/instance-type-family :opt]
     "instanceFamily"              [:aws.v1.rds.product/instance-family :aws.v1.rds.product/instance-family :opt]
     "dedicatedEbsThroughput"      [:aws.v1.rds.product/dedicated-ebs-throughput string? :opt]
     "engineMediaType"             [:aws.v1.rds.product/engine-media-type :aws.v1.rds.product/engine-media-type :opt]
     "deploymentModel"             [:aws.v1.rds.product/deployment-model :aws.v1.rds.product/deployment-model :opt]
     "acu"                         [:aws.v1.rds.product/acu :aws.v1.rds.product/acu :opt]}))

(s/def :aws.v1.rds.pricing/price-dimensions
  (h/strictly-conform-json-object2
    price-dimensions-spec
    {"rateCode"     [:aws.v1.pricing/rate-code string?]
     "description"  [:aws.v1.pricing/description string?]
     "beginRange"   [nil #{"0"} :opt]
     "endRange"     [nil #{"1000000" "Inf"} :opt]
     "unit"         [:aws.v1.pricing/unit (s/conformer #(case %
                                                          "ACU-Hr" :aws.v1.pricing.unit/acu-hour
                                                          "ACU-Months" :aws.v1.pricing.unit/acu-month
                                                          "API Calls" :aws.v1.pricing.unit/api-call
                                                          "CR-Hr" :aws.v1.pricing.unit/cr-hour
                                                          "GB" :aws.v1.pricing.unit/gb
                                                          "GB-Mo" :aws.v1.pricing.unit/gb-month
                                                          "Hrs" :aws.v1.pricing.unit/hour
                                                          "IOPS-Mo" :aws.v1.pricing.unit/iops-month
                                                          "IOs" :aws.v1.pricing.unit/ios
                                                          "Quantity" :aws.v1.pricing.unit/quantity
                                                          "vCPU-Hours" :aws.v1.pricing.unit/vcpu-hour
                                                          "vCPU-Months" :aws.v1.pricing.unit/vcpu-month
                                                          ::s/invalid))]
     "pricePerUnit" [:aws.v1.pricing.price-per-unit/value
                     (s/conformer #(try (Double/parseDouble (get % "USD"))
                                     (catch java.lang.NumberFormatException _ ::s/invalid)))]
     "appliesTo"    [nil (s/coll-of string?)]}))

(def schema [#:db{:ident       :aws.v1.rds.product/database-edition
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/clock-speed
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/processor-architecture
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/processor-features
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/min-volume-size
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/vcpu
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/physical-processor
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/group
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/servicename
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/servicecode
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/max-volume-size
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/deployment-option
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/storage
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/group-description
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/engine-code
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/normalization-size-factor
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/enhanced-networking-supported
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/storage-media
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/location
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/network-performance
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/operation
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/location-type
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/memory
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/volume-type
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/usage-type
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/instance-type
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/database-engine
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/license-model
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/current-generation
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/instance-type-family
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/instance-family
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.rds.product/dedicated-ebs-throughput
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}
             {:db/ident       :aws.v1.rds.product/engine-media-type
              :db/valueType   :db.type/string
              :db/cardinality :db.cardinality/one}
             {:db/ident       :aws.v1.rds.product/deployment-model
              :db/valueType   :db.type/string
              :db/cardinality :db.cardinality/one}
             {:db/ident       :aws.v1.rds.product/volume-name
              :db/valueType   :db.type/string
              :db/cardinality :db.cardinality/one}
             {:db/ident       :aws.v1.rds.product/acu
              :db/valueType   :db.type/string
              :db/cardinality :db.cardinality/one}])

(defn product-entity-map
  [sku product]
  (let [{:strs [sku productFamily attributes]} product
        sku (h/conform! :aws.v1.product/sku sku {:product product})
        product-family (h/conform! string? productFamily {:product product})
        attributes (h/conform! :aws.v1.rds.product/attributes attributes {:product product})]
    (merge {:aws.v1.product/sku            sku
            :aws.v1.product/product-family productFamily}
      attributes)))

(defn price-entity-map [term sku offer price]
  (let [{:strs [offerTermCode sku effectiveDate priceDimensions]} price
        sku (h/conform! :aws.v1.product/sku sku {:price price})
        offer-term-code (h/conform! :aws.v1.pricing/offer-term-code offerTermCode {:price price})
        effective-date (h/conform! :aws.v1.pricing/effective-date effectiveDate {:price price})
        price-dimensions (into [] (map #(h/conform! :aws.v1.rds.pricing/price-dimensions % {:price price})) (vals priceDimensions))]
    (let [term-key (case term "OnDemand" :aws.v1.term/on-demand "Reserved" :aws.v1.term/reserved)]
      {:aws.v1.product/sku sku
       term-key            {:aws.v1.pricing/offer-term-code offer-term-code
                            :aws.v1.pricing/effective-date  effective-date
                            :aws.v1.pricing/dimensions      price-dimensions}})))

(comment

  (def client (d/client {:server-type        :peer-server
                         :access-key         "myaccesskey"
                         :secret             "mysecret"
                         :endpoint           "localhost:8999"
                         :validate-hostnames false}))

  (def conn (d/connect client {:db-name "workspace"}))

  (def db (d/db conn))

  (let [[a b]
        (d/q '[:find (pull ?e [*]) #_?price-val
               :in $ ?aws-region ?instance-type ?database-engine ?deployment-option
               :where
               [?e :aws.v1.rds.product/instance-type "db.t3.small"]
               [?e :aws.v1.rds.product/location ?aws-region]
               [?e :aws.v1.rds.product/database-engine ?database-engine]
               [?e :aws.v1.term/on-demand ?t]
               [?t :aws.v1.pricing/dimensions ?d]
               [?d :aws.v1.pricing/unit :aws.v1.pricing.unit/hour]
               [?d :aws.v1.pricing.price-per-unit/value ?price-val]]
          db "us-east-1" "db.t3.micro" "PostgreSQL" true)]
    b)

  )
