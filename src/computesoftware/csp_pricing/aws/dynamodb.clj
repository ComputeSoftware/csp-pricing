(ns computesoftware.csp-pricing.aws.dynamodb
  (:require
    [camel-snake-kebab.core :as csk]
    [clojure.data.json :as json]
    [clojure.set :as s]
    [clojure.walk :as walk]
    [malli.core :as m]
    [malli.transform :as mt]
    [malli.util :as mu]))

(def region-enum-schema
  [:enum
   "af-south-1"
   "ap-east-1"
   "ap-northeast-1"
   "ap-northeast-2"
   "ap-northeast-3"
   "ap-south-1"
   "ap-southeast-1"
   "ap-southeast-2"
   "ap-southeast-3"
   "ca-central-1"
   "eu-central-1"
   "eu-north-1"
   "eu-south-1"
   "eu-west-1"
   "eu-west-2"
   "eu-west-3"
   "me-south-1"
   "sa-east-1"
   "us-east-1"
   "us-east-2"
   "us-gov-east-1"
   "us-gov-west-1"
   "us-west-1"
   "us-west-2"])

(def location-enum-schema
  [:enum
   "AWS GovCloud (US-East)"
   "AWS GovCloud (US-West)"
   "Africa (Cape Town)"
   "Asia Pacific (Hong Kong)"
   "Asia Pacific (Jakarta)"
   "Asia Pacific (Mumbai)"
   "Asia Pacific (Osaka)"
   "Asia Pacific (Seoul)"
   "Asia Pacific (Singapore)"
   "Asia Pacific (Sydney)"
   "Asia Pacific (Tokyo)"
   "Canada (Central)"
   "EU (Frankfurt)"
   "EU (Ireland)"
   "EU (London)"
   "EU (Milan)"
   "EU (Paris)"
   "EU (Stockholm)"
   "Middle East (Bahrain)"
   "South America (Sao Paulo)"
   "US East (N. Virginia)"
   "US East (Ohio)"
   "US West (N. California)"
   "US West (Oregon)"])

(def operation-enum-schema
  [:enum "" "CommittedThroughput" "DelegatedOperations" "GetRecords" "PayPerRequestThroughput"])

(def volume-type-enum-schema
  [:enum
   "Amazon DynamoDB - Backup Restore Size"
   "Amazon DynamoDB - Export Size"
   "Amazon DynamoDB - Indexed DataStore"
   "Amazon DynamoDB - Indexed DataStore - IA"
   "Amazon DynamoDB - On-Demand Backup Storage"
   "Amazon DynamoDB - Point-In-Time-Restore (PITR) Backup Storage"])

(def group-enum-schema
  [:enum
   "DDB-ElasticViews"
   "DDB-Kinesis"
   "DDB-ReadUnits"
   "DDB-ReadUnitsIA"
   "DDB-ReplicatedWriteUnits"
   "DDB-ReplicatedWriteUnitsIA"
   "DDB-StreamsReadRequests"
   "DDB-WriteUnits"
   "DDB-WriteUnitsIA"])

(def group-description-enum-schema
  [:enum
   "Change Data Capture Units for AWS Glue Elastic Views"
   "Change Data Capture Units for Kinesis Data Streams"
   "DynamoDB PayPerRequest Read Request Units"
   "DynamoDB PayPerRequest Read Request Units IA"
   "DynamoDB PayPerRequest Replicated Write Request Units"
   "DynamoDB PayPerRequest Replicated Write Request Units IA"
   "DynamoDB PayPerRequest Write Request Units"
   "DynamoDB PayPerRequest Write Request Units IA"
   "DynamoDB Provisioned Read Units"
   "DynamoDB Provisioned Read Units IA"
   "DynamoDB Provisioned Replicated Write Units"
   "DynamoDB Provisioned Replicated Write Units IA"
   "DynamoDB Provisioned Write Units"
   "DynamoDB Provisioned Write Units IA"
   "DynamoDB Streams read request (GetRecords API)"])

;; Raw version generated using malli, see rich comment.
(def product-input-schema
  (-> (m/schema
        [:map
         [:sku string?]
         [:attributes
          [:map
           [:groupDescription {:optional true} group-description-enum-schema]
           [:usagetype string?]
           [:group {:optional true} group-enum-schema]
           [:operation operation-enum-schema]
           [:servicecode [:enum "AmazonDynamoDB"]]
           [:regionCode region-enum-schema]
           [:servicename [:enum "Amazon DynamoDB"]]
           [:locationType [:enum "AWS Region"]]
           [:volumeType {:optional true} volume-type-enum-schema]
           [:location location-enum-schema]]]
         [:productFamily {:optional true} string?]])
    (mu/closed-schema)))

(def ondemand-term-units
  {"ChangeDataCaptureUnits"          :aws.v1.pricing.unit/change-data-capture-unit
   "GB"                              :aws.v1.pricing.unit/gb
   "GB-Mo"                           :aws.v1.pricing.unit/gb-mo
   "GB-Month"                        :aws.v1.pricing.unit/gb-month
   "ReadCapacityUnit-Hrs"            :aws.v1.pricing.unit/read-capacity-unit-hour
   "ReadRequestUnits"                :aws.v1.pricing.unit/read-request-unit
   "ReplicatedWriteCapacityUnit-Hrs" :aws.v1.pricing.unit/replicated-write-capacity-unit-hour
   "ReplicatedWriteRequestUnits"     :aws.v1.pricing.unit/replicated-write-request-unit
   "Requests"                        :aws.v1.pricing.unit/request
   "WriteCapacityUnit-Hrs"           :aws.v1.pricing.unit/write-capacity-unit-hour
   "WriteRequestUnits"               :aws.v1.pricing.unit/write-request-unit})

(def ondemand-term-input-schema
  (-> (m/schema
        [:map
         [:offerTermCode string?]
         [:sku string?]
         [:effectiveDate string?]
         [:priceDimensions
          [:vector
           [:map
            [:rateCode string?]
            [:description string?]
            [:beginRange string?]
            [:endRange string?]
            [:unit (into [:enum] (keys ondemand-term-units))]
            [:pricePerUnit [:map [:USD string?]]]
            [:appliesTo [:vector any?]]]]]
         [:termAttributes [:map]]])
    (mu/closed-schema)))

(def price-per-unit-schema
  [double? {:decode/json {:enter (fn [p] (Double/parseDouble (:USD p)))}}])

(def ondemand-term-unit-schema
  [keyword? {:decode/json {:enter (fn [x] (get ondemand-term-units x))}}])

(def ondemand-term-output-schema
  (-> ondemand-term-input-schema
    (mu/assoc :effectiveDate inst?)
    (mu/assoc-in [:priceDimensions 0 :pricePerUnit] price-per-unit-schema)
    (mu/assoc-in [:priceDimensions 0 :unit] ondemand-term-unit-schema)))

(def reserved-term-units
  {"Quantity"              :aws.v1.pricing.unit/quantity
   "ReadCapacityUnit-Hrs"  :aws.v1.pricing.unit/read-capacity-unit-hour
   "WriteCapacityUnit-Hrs" :aws.v1.pricing.unit/write-capacity-unit-hour})

(def reserved-term-unit-schema
  [keyword? {:decode/json {:enter (fn [x] (get reserved-term-units x))}}])

(def reserved-term-input-schema
  (-> (m/schema
        [:map
         [:offerTermCode string?]
         [:sku string?]
         [:effectiveDate string?]
         [:priceDimensions
          [:vector
           [:map
            [:rateCode string?]
            [:description [:enum
                           "Amazon DynamoDB, Reserved Read Capacity used this month"
                           "Amazon DynamoDB, Reserved Write Capacity used this month"
                           "Upfront Fee"]]
            [:unit (into [:enum] (keys reserved-term-units))]
            [:pricePerUnit [:map [:USD string?]]]
            [:beginRange {:optional true} string?]
            [:endRange {:optional true} string?]
            [:appliesTo {:optional true} [:vector any?]]]]]
         [:termAttributes [:map [:LeaseContractLength string?] [:OfferingClass string?] [:PurchaseOption string?]]]])
    (mu/closed-schema)))

(def reserved-term-output-schema
  (-> reserved-term-input-schema
    (mu/assoc :effectiveDate inst?)
    (mu/assoc-in [:priceDimensions 0 :pricePerUnit] price-per-unit-schema)
    (mu/assoc-in [:priceDimensions 0 :unit] reserved-term-unit-schema)))

(defn- namespace-keys [ns m]
  (update-keys m #(->> (csk/->kebab-case %) (name) (keyword ns))))

(defn- filter-vals [val-pred? m]
  (into {} (for [[k v] m :when (val-pred? v)] [k v])))

(defn product-entity-map
  [sku product]
  (let [{:keys [sku productFamily attributes] :as product} (walk/keywordize-keys product)]
    (if-let [error (m/explain product-input-schema product)]
      (throw (ex-info "Invalid DynamoDB product" error))
      (let [attributes (namespace-keys "aws.v1.dynamodb.product" attributes)
            product (assoc attributes :aws.v1.product/sku sku :aws.v1.product/product-family productFamily)]
        (filter-vals some? product)))))

(def price-attr-rename-mappings
  {:aws.v1.pricing/price-per-unit :aws.v1.pricing.price-per-unit/value})

(def price-attr-exclusions
  [:aws.v1.pricing/begin-range
   :aws.v1.pricing/end-range
   :aws.v1.pricing/applies-to])

(defn price-entity-map [term sku offer price]
  (let [price (-> price
                (walk/keywordize-keys)
                (update :priceDimensions (comp vec vals)))
        [schema out-schema term-key] (case term
                                       "Reserved" [reserved-term-input-schema reserved-term-output-schema :aws.v1.term/reserved]
                                       "OnDemand" [ondemand-term-input-schema ondemand-term-output-schema :aws.v1.term/on-demand]
                                       (throw (ex-info "Unknown term" {:unknown-term term})))]
    (if-let [error (m/explain schema price)]
      (throw (ex-info "Invalid DynamoDB price" error))
      (let [price (m/decode out-schema price mt/json-transformer)
            price-dimensions (->> price
                               :priceDimensions
                               (mapv
                                 (fn [price-dimension]
                                   (as-> (namespace-keys "aws.v1.pricing" price-dimension) m
                                     (s/rename-keys m price-attr-rename-mappings)
                                     (apply dissoc m price-attr-exclusions)))))
            term-attributes (some->> price
                              :termAttributes
                              (namespace-keys "aws.v1.pricing.term-attribute"))
            term {:aws.v1.pricing/offer-term-code (:offerTermCode price)
                  :aws.v1.pricing/effective-date  (:effectiveDate price)
                  :aws.v1.pricing/dimensions      price-dimensions}]
        {:aws.v1.product/sku sku
         term-key            (if (not-empty term-attributes)
                               (assoc term :aws.v1.pricing/term-attributes term-attributes)
                               term)}))))

(def schema
  (concat
    [#:db{:ident       :aws.v1.dynamodb.product/region-code
          :valueType   :db.type/string
          :cardinality :db.cardinality/one}
     #:db{:ident       :aws.v1.dynamodb.product/operation
          :valueType   :db.type/string
          :cardinality :db.cardinality/one}
     #:db{:ident       :aws.v1.dynamodb.product/location
          :valueType   :db.type/string
          :cardinality :db.cardinality/one}
     #:db{:ident       :aws.v1.dynamodb.product/group-description
          :valueType   :db.type/string
          :cardinality :db.cardinality/one}
     #:db{:ident       :aws.v1.dynamodb.product/location-type
          :valueType   :db.type/string
          :cardinality :db.cardinality/one}
     #:db{:ident       :aws.v1.dynamodb.product/group
          :valueType   :db.type/string
          :cardinality :db.cardinality/one}
     #:db{:ident       :aws.v1.dynamodb.product/usagetype
          :valueType   :db.type/string
          :cardinality :db.cardinality/one}
     #:db{:ident       :aws.v1.dynamodb.product/servicename
          :valueType   :db.type/string
          :cardinality :db.cardinality/one}
     #:db{:ident       :aws.v1.product/sku
          :valueType   :db.type/string
          :cardinality :db.cardinality/one}
     #:db{:ident       :aws.v1.dynamodb.product/servicecode
          :valueType   :db.type/string
          :cardinality :db.cardinality/one}
     #:db{:ident       :aws.v1.dynamodb.product/volume-type
          :valueType   :db.type/string
          :cardinality :db.cardinality/one}]
    (mapv
      (fn [unit]
        {:db/ident unit})
      (vals ondemand-term-units))
    (mapv
      (fn [unit]
        {:db/ident unit})
      (vals reserved-term-units))))

; For schema experimentation.
(comment
  (require '[clojure.data.json :as json])
  (require '[malli.core :as m])
  (require '[malli.provider :as mp])

  ;; Parsing the product part from the dynamodb json file
  ;; First in import.clj call: (download-services download-path #{:aws/dynamodb} nil)
  ;; to fetch the file into the /tmp directory.
  (def dynamodb-json
    (with-open [in (java.util.zip.GZIPInputStream.
                     (clojure.java.io/input-stream
                       "/tmp/aws/dynamodb/20220103-090515/in/dynamodb.json.gz"))]
      (json/read-str (slurp in) :key-fn keyword)))

  (def products (-> dynamodb-json :products (vals)))
  (def reserved-terms (->> dynamodb-json :terms :Reserved (vals) (mapcat vals) (map (fn [term] (update term :priceDimensions vals)))))
  (def ondemand-terms (->> dynamodb-json :terms :OnDemand (vals) (mapcat vals) (map (fn [term] (update term :priceDimensions vals)))))


  (type ondemand-terms)
  (type reserved-terms)
  (type products)
  (filter #(= "R6PXMNYCEDGZ2EYN" (:sku %)) products)

  (filter #(= "WriteCapacityUnit-Hrs" (get-in % [:attributes :usagetype])) products)



  (let [provider (mp/provider)]                             ;; NOTE: more recent version of Malli than in common deps is multiple orders of magnitude faster
    (provider products)
    (provider reserved-terms))

  (defn ->enum [value-set]
    (-> value-set vec sort (into [:enum]) vec))

  (require '[clojure.pprint :refer [pprint]])

  (pprint
    (->> [:groupDescription :usagetype :group :operation :servicecode :regionCode :servicename :locationType :volumeType :location]
      (map
        (fn [attr]
          (let [products (-> dynamodb-json :products vals)
                value-set (->> products (map #(get-in % [:attributes attr])) set)]
            ;; Until Malli has enum support, use following heuristics to figure out candidates for enum values
            (if (< (count value-set) (* 2 (Math/sqrt (count products))))
              {:attribute            attr
               :distinct-value-count (count value-set)
               :enum-schema          (->enum value-set)}
              {:attribute            attr
               :distinct-value-count (count value-set)}))))
      (sort-by :distinct-value-count)))

  (pprint
    (->> [:rateCode :description :beginRange :endRange :unit :pricePerUnit :appliesTo]
      (map
        (fn [attr]
          (let [price-dimensions (mapcat :priceDimensions #_ondemand-terms reserved-terms)
                value-set (->> price-dimensions (map #(get % attr)) set)]
            (if (< (count value-set) (* 2 (Math/sqrt (count price-dimensions))))
              {:attribute            attr
               :distinct-value-count (count value-set)
               :enum-schema          (->enum value-set)}
              {:attribute            attr
               :distinct-value-count (count value-set)}))))))


  ;(let [provider (mp/provider {::mp/map-of-threshold 20})]
  ;  (pprint (provider [ondemand-terms])))

  )
