(ns computesoftware.csp-pricing.aws.common
  (:require
    [clojure.spec.alpha :as s]
    [computesoftware.csp-pricing.impl.spec-helpers :as h])
  (:import (java.time Instant OffsetDateTime)
           (java.time.format DateTimeFormatter)
           (java.util Date)))

(def schema [;; meta
             #:db{:ident       :aws.v1.meta/format-version,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.meta/disclaimer,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.meta/offer-code,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.meta/version,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.meta/publication-date,
                  :valueType   :db.type/instant
                  :cardinality :db.cardinality/one}

             ;; product
             #:db{:ident       :aws.v1.product/location-type,
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.product/operation,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.product/product-family
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.product/service-code,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.product/service-name,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.product/sku,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one
                  :db/unique   :db.unique/identity}

             ;; pricing
             #:db{:ident       :aws.v1.term/on-demand
                  :valueType   :db.type/ref
                  :cardinality :db.cardinality/many
                  :isComponent true}

             #:db{:ident       :aws.v1.term/reserved
                  :valueType   :db.type/ref
                  :cardinality :db.cardinality/many
                  :isComponent true}

             #:db{:ident       :aws.v1.pricing/dimensions
                  :valueType   :db.type/ref
                  :cardinality :db.cardinality/many
                  :isComponent true}

             #:db{:ident       :aws.v1.pricing/description,
                  :valueType   :db.type/string,
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.pricing/effective-date
                  :valueType   :db.type/instant
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.pricing/offer-term-code
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.pricing.price-per-unit/value
                  :valueType   :db.type/double
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.pricing/rate-code
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.pricing/unit
                  :valueType   :db.type/ref,
                  :cardinality :db.cardinality/one}

             {:db/ident :aws.v1.pricing.unit/acu-hour}
             {:db/ident :aws.v1.pricing.unit/acu-month}
             {:db/ident :aws.v1.pricing.unit/api-call}
             {:db/ident :aws.v1.pricing.unit/count}
             {:db/ident :aws.v1.pricing.unit/cr-hour}
             {:db/ident :aws.v1.pricing.unit/gb}
             {:db/ident :aws.v1.pricing.unit/gb-month}
             {:db/ident :aws.v1.pricing.unit/gb-hour}
             {:db/ident :aws.v1.pricing.unit/gbps-hour}
             {:db/ident :aws.v1.pricing.unit/hour}
             {:db/ident :aws.v1.pricing.unit/idle-lcu-hour}
             {:db/ident :aws.v1.pricing.unit/iops-month}
             {:db/ident :aws.v1.pricing.unit/ios}
             {:db/ident :aws.v1.pricing.unit/lcu-hour}
             {:db/ident :aws.v1.pricing.unit/month}
             {:db/ident :aws.v1.pricing.unit/quantity}
             {:db/ident :aws.v1.pricing.unit/request}
             {:db/ident :aws.v1.pricing.unit/snapshot-api-unit}
             {:db/ident :aws.v1.pricing.unit/user}
             {:db/ident :aws.v1.pricing.unit/vcpu-hour}
             {:db/ident :aws.v1.pricing.unit/vcpu-month}
             {:db/ident :aws.v1.pricing.unit/mibps-month}
             {:db/ident :aws.v1.pricing.unit/gibps-month}

             #:db{:ident       :aws.v1.pricing/term-attributes
                  :valueType   :db.type/ref
                  :cardinality :db.cardinality/one
                  :isComponent true}

             #:db{:ident       :aws.v1.pricing.term-attribute/lease-contract-length
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.pricing.term-attribute/offering-class
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.pricing.term-attribute/purchase-option
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}])

(defn aws-date-string->date-inst [d]
  (try
    (-> (OffsetDateTime/parse d DateTimeFormatter/ISO_DATE_TIME)
      (Instant/from)
      (Date/from))
    (catch java.time.format.DateTimeParseException _ ::s/invalid)))

(s/def :aws.v1/meta
  (h/strictly-conform-json-object
    {"formatVersion"   [:aws.v1.meta/format-version {"v1.0" "v1.0"}]
     "disclaimer"      [nil string?]
     "offerCode"       [:aws.v1.meta/offer-code string? #_{"AmazonWorkSpaces" "AmazonWorkSpaces"
                                                           "AmazonEC2"        "AmazonEC2"
                                                           "AmazonRDS"        "AmazonRDS"
                                                           "AmazonAppStream"  "AmazonAppStream"}]
     "version"         [:aws.v1.meta/version string?]
     "publicationDate" [:aws.v1.meta/publication-date (s/conformer aws-date-string->date-inst)]
     "attributesList"  [nil map?]}))

(s/def :aws.v1.product/sku
  (s/conformer #(or (re-matches #"[A-Z0-9]{16}" %) ::s/invalid)))

(s/def :aws.v1.pricing/offer-term-code
  (s/conformer #(or (re-find #"[A-Z0-9]{10}" %) ::s/invalid)))

(s/def :aws.v1.pricing/effective-date (s/conformer aws-date-string->date-inst))
