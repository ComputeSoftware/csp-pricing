(ns computesoftware.csp-pricing.aws.workspaces
  (:require [clojure.spec.alpha :as s]
            [computesoftware.csp-pricing.aws.common]
            [computesoftware.csp-pricing.impl.spec-helpers :as h]
            [computesoftware.csp-pricing.qbe :as qbe]
            [kwill.anomkit :as ak]))

;; ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
;; ┃                 ___ _      _                                                 ┃
;; ┃                / __| |___ (_)_  _ _ _ ___   ____ __  ___ __ ___              ┃
;; ┃               | (__| / _ \| | || | '_/ -_) (_-< '_ \/ -_) _(_-<              ┃
;; ┃                \___|_\___// |\_,_|_| \___| /__/ .__/\___\__/__/              ┃
;; ┃                         |__/                  |_|                            ┃
;; ┃                                                                              ┃
;; ┃                                                                              ┃
;; ┃                       Specs for the Workspaces domain.                       ┃
;; ┃                                                                              ┃
;; ┃                                                                              ┃
;; ┃                                                                              ┃
;; ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

(defmulti product-attributes-spec #(get % "servicecode"))

(def hardware-spec
  {"servicename"       [:aws.v1.product/service-name #{"Amazon WorkSpaces"}]
   "servicecode"       [:aws.v1.product/service-code #{"AmazonWorkSpaces"}]
   "locationType"      [:aws.v1.product/location-type #{"AWS Region"}]
   "operation"         [:aws.v1.product/operation #{""}]
   "location"          [:aws.v1.workspaces.product/location
                        {"AWS GovCloud (US)"         "us-gov-west-1"
                         "AWS GovCloud (US-West)"    "us-gov-west-1"
                         "Any"                       "any"
                         "Asia Pacific (Mumbai)"     "ap-south-1"
                         "Asia Pacific (Seoul)"      "ap-northeast-2"
                         "Asia Pacific (Singapore)"  "ap-southeast-1"
                         "Asia Pacific (Sydney)"     "ap-southeast-2"
                         "Asia Pacific (Tokyo)"      "ap-northeast-1"
                         "Canada (Central)"          "ca-central-1"
                         "EU (Frankfurt)"            "eu-central-1"
                         "EU (Ireland)"              "eu-west-1"
                         "EU (London)"               "eu-west-2"
                         "South America (Sao Paulo)" "sa-east-1"
                         "US East (N. Virginia)"     "us-east-1"
                         "US West (Oregon)"          "us-west-2"}]
   "license"           [:aws.v1.workspaces.product/license
                        {"Included"               :aws.v1.workspaces.product.license/included
                         "Bring Your Own License" :aws.v1.workspaces.product.license/byol
                         "None"                   :aws.v1.workspaces.product.license/none}]
   "uservolume"        [:aws.v1.workspaces.product/user-volume {"100 GB" 100 "10 GB" 10 "50 GB" 50} :opt]
   "vcpu"              [:aws.v1.workspaces.product/vcpu {"1" 1 "2" 2 "4" 4 "8" 8 "16" 16} :opt]
   "group"             [:aws.v1.workspaces.product/group
                        {"User"  :aws.v1.workspaces.product.group/billed-by-month
                         "Usage" :aws.v1.workspaces.product.group/billed-by-hour}]
   "groupDescription"  [:aws.v1.workspaces.product/group-description `string?]
   "storage"           [:aws.v1.workspaces.product/storage `string? :opt]
   "runningMode"       [:aws.v1.workspaces.product/running-mode
                        {"AlwaysOn" :aws.v1.workspaces.product.running-mode/always-on
                         "AutoStop" :aws.v1.workspaces.product.running-mode/auto-stop}]
   "softwareIncluded"  [:aws.v1.workspaces.product/software-included `string?]
   "bundle"            [:aws.v1.workspaces.product/bundle
                        `(s/conformer
                           #(or (some-> (re-matches #"((?:GraphicsPro|PowerPro|Performance|Graphics|Value|Standard|Power)(?:\sPlus)?)(?:-\d+)?" %)
                                  second)
                              ::s/invalid))]
   "bundleGroup"       [:aws.v1.workspaces.product/bundle-group
                        #{"Graphics"
                          "GraphicsPro"
                          "Performance"
                          "Power"
                          "PowerPro"
                          "Standard"
                          "Storage"
                          "Value"}]
   "bundleDescription" [nil string? :opt]
   "memory"            [:aws.v1.workspaces.product/memory
                        {"2 GB"  2.0 "4 GB" 4.0 "7.5 GB" 7.5
                         "8 GB"  8.0 "15 GB" 15.0 "16 GB" 16.0
                         "32 GB" 32.0 "122 GB" 122.0} :opt]
   "usagetype"         [:aws.v1.workspaces.product/usage-type `string?]
   "operatingSystem"   [:aws.v1.workspaces.product/operating-system
                        {"Windows"      :aws.v1.workspaces.product.operating-system/windows
                         "Amazon Linux" :aws.v1.workspaces.product.operating-system/amazon-linux}]
   "resourceType"      [:aws.v1.workspaces.product/resource-type {"Hardware" :aws.v1.workspaces.product/hardware
                                                                  ;; yes, apparently aws categorizes hardware under software now...
                                                                  "Software" :aws.v1.workspaces.product/software}]
   "rootvolume"        [:aws.v1.workspaces.product/root-volume {"80 GB" 80 "100 GB" 100 "175 GB" 175} :opt]})

(s/def :aws.v1.workspaces.product.attributes/hardware
  (h/strictly-conform-json-object2 hardware-multi-spec hardware-spec))

(def software-spec
  (into (dissoc hardware-spec "bundleGroup" "uservolume" "rootvolume")
    {"group"        [:aws.v1.workspaces.product/group {"User" :aws.v1.workspaces.product.group/billed-by-month}]
     "runningMode"  [:aws.v1.workspaces.product/running-mode {"AlwaysOn" :aws.v1.workspaces.product.running-mode/always-on}]
     "bundle"       [:aws.v1.workspaces.product/bundle
                     `(s/conformer
                        #(or (some-> (re-matches #"((?:GraphicsPro|PowerPro|Performance|Graphics|Value|Standard|Power)(?:\sPlus)?)(?:-\d+)?" %)
                               second)
                           ::s/invalid))]
     "resourceType" [:aws.v1.workspaces.product/resource-type {"Software" :aws.v1.workspaces.product/software}]}))

(s/def :aws.v1.workspaces.product.attributes/software
  (h/strictly-conform-json-object2 software-multi-spec software-spec))

(def storage-spec
  (into hardware-spec
    {"license"         [:aws.v1.workspaces.product/license
                        {"Root Volume Usage" :aws.v1.workspaces.product.license/root-volume-usage
                         "User Volume Usage" :aws.v1.workspaces.product.license/user-volume-usage}]
     "group"           [:aws.v1.workspaces.product/group {"User" :aws.v1.workspaces.product.group/billed-by-month}]
     "runningMode"     [:aws.v1.workspaces.product/running-mode {"Not Applicable" :aws.v1.workspaces.product.running-mode/not-applicable}]
     "bundle"          [:aws.v1.workspaces.product/bundle {"Storage" "Storage"}]
     "memory"          [nil #{"0"}]
     "operatingSystem" [nil #{"Windows"}]
     "uservolume"      [nil #{"0 GB" "Custom"}]
     "vcpu"            [nil #{"0"}]
     "rootvolume"      [nil #{"0 GB" "Custom"}]}))

(s/def :aws.v1.workspaces.product.attributes/storage (h/strictly-conform-json-object2 storage-multi-spec storage-spec))

(defmulti resource-type-spec #(get % "resourceType"))

(defmethod resource-type-spec "Software"
  [_]
  :aws.v1.workspaces.product.attributes/software)

(defmethod resource-type-spec "Hardware"
  [_]
  (s/or :aws.v1.workspaces.product.attributes/storage :aws.v1.workspaces.product.attributes/storage
    :aws.v1.workspaces.product.attributes/hardware :aws.v1.workspaces.product.attributes/hardware))

(s/def ::product-workspaces-spec (s/multi-spec resource-type-spec #(assoc %1 "resourceType" %2)))
(defmethod product-attributes-spec "AmazonWorkSpaces" [_]
  (s/or :aws.v1.workspaces.product.attributes/storage :aws.v1.workspaces.product.attributes/storage
    :aws.v1.workspaces.product.attributes/hardware :aws.v1.workspaces.product.attributes/hardware
    :aws.v1.workspaces.product.attributes/software :aws.v1.workspaces.product.attributes/software))

(s/def :aws.v1.workspaces.product/attributes (s/multi-spec product-attributes-spec #(assoc %1 "servicecode" %2)))

(s/def :aws.v1.workspaces.pricing/price-dimensions
  (h/strictly-conform-json-object2
    price-dimensions-spec
    {"rateCode"     [:aws.v1.pricing/rate-code string?]
     "description"  [:aws.v1.pricing/description string?]
     "beginRange"   [nil string?]
     "endRange"     [nil string?]
     "unit"         [:aws.v1.pricing/unit
                     (s/conformer #(case %
                                     ("Hour" "hour") :aws.v1.pricing.unit/hour
                                     ("Month" "MONTH") :aws.v1.pricing.unit/month
                                     "GB-Month" :aws.v1.pricing.unit/gb-month
                                     ::s/invalid))]
     "pricePerUnit" [:aws.v1.pricing.price-per-unit/value
                     (s/conformer #(try (Double/parseDouble (get % "USD"))
                                     (catch java.lang.NumberFormatException _ ::s/invalid)))]
     "appliesTo"    [nil (s/coll-of string?)]}))


;; ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
;; ┃  ___       _             _      ___     _                                  _ ┃
;; ┃ |   \ __ _| |_ ___ _ __ (_)__  / __| __| |_  ___ _ __  __ _   __ _ _ _  __| |┃
;; ┃ | |) / _` |  _/ _ \ '  \| / _| \__ \/ _| ' \/ -_) '  \/ _` | / _` | ' \/ _` |┃
;; ┃ |___/\__,_|\__\___/_|_|_|_\__| |___/\__|_||_\___|_|_|_\__,_| \__,_|_||_\__,_|┃
;; ┃                                                                              ┃
;; ┃              ___           __ _                    _   _                     ┃
;; ┃             / __|___ _ _  / _(_)__ _ _  _ _ _ __ _| |_(_)___ _ _             ┃
;; ┃            | (__/ _ \ ' \|  _| / _` | || | '_/ _` |  _| / _ \ ' \            ┃
;; ┃             \___\___/_||_|_| |_\__, |\_,_|_| \__,_|\__|_\___/_||_|           ┃
;; ┃                                |___/                                         ┃
;; ┃                                                                              ┃
;; ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

(def schema [#:db{:ident       :aws.v1.workspaces.product/location
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.workspaces.product/attributes
                  :valueType   :db.type/ref
                  :cardinality :db.cardinality/one}
             {:db/ident :aws.v1.workspaces.product.attributes/software}
             {:db/ident :aws.v1.workspaces.product.attributes/storage}
             {:db/ident :aws.v1.workspaces.product.attributes/hardware}

             #:db{:ident       :aws.v1.workspaces.product/vcpu
                  :valueType   :db.type/long
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.workspaces.product/memory
                  :valueType   :db.type/double
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.workspaces.product/storage
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.workspaces.product/operating-system
                  :valueType   :db.type/ref
                  :cardinality :db.cardinality/one}
             {:db/ident :aws.v1.workspaces.product.operating-system/windows}
             {:db/ident :aws.v1.workspaces.product.operating-system/amazon-linux}

             #:db{:ident       :aws.v1.workspaces.product/group
                  :valueType   :db.type/ref
                  :cardinality :db.cardinality/one}
             {:db/ident :aws.v1.workspaces.product.group/billed-by-month}
             {:db/ident :aws.v1.workspaces.product.group/billed-by-hour}

             #:db{:ident       :aws.v1.workspaces.product/group-description
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.workspaces.product/usage-type
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.workspaces.product/bundle
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}
             #:db{:ident       :aws.v1.workspaces.product/bundle-group
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.workspaces.product/license
                  :valueType   :db.type/ref
                  :cardinality :db.cardinality/one}
             {:db/ident :aws.v1.workspaces.product.license/included}
             {:db/ident :aws.v1.workspaces.product.license/root-volume-usage}
             {:db/ident :aws.v1.workspaces.product.license/none}
             {:db/ident :aws.v1.workspaces.product.license/byol}
             {:db/ident :aws.v1.workspaces.product.license/user-volume-usage}

             #:db{:ident       :aws.v1.workspaces.product/resource-type
                  :valueType   :db.type/ref
                  :cardinality :db.cardinality/one}
             {:db/ident :aws.v1.workspaces.product/hardware}
             {:db/ident :aws.v1.workspaces.product/software}

             #:db{:ident       :aws.v1.workspaces.product/root-volume
                  :valueType   :db.type/long
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.workspaces.product/running-mode
                  :valueType   :db.type/ref
                  :cardinality :db.cardinality/one}
             {:db/ident :aws.v1.workspaces.product.running-mode/always-on}
             {:db/ident :aws.v1.workspaces.product.running-mode/auto-stop}
             {:db/ident :aws.v1.workspaces.product.running-mode/not-applicable}

             #:db{:ident       :aws.v1.workspaces.product/software-included
                  :valueType   :db.type/string
                  :cardinality :db.cardinality/one}

             #:db{:ident       :aws.v1.workspaces.product/user-volume
                  :valueType   :db.type/long
                  :cardinality :db.cardinality/one}])


;; ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
;; ┃                                                                              ┃
;; ┃                                                                              ┃
;; ┃                                                                              ┃
;; ┃    ___       _          _  _                    _ _         _   _            ┃
;; ┃   |   \ __ _| |_ __ _  | \| |___ _ _ _ __  __ _| (_)_____ _| |_(_)___ _ _    ┃
;; ┃   | |) / _` |  _/ _` | | .` / _ \ '_| '  \/ _` | | |_ / _` |  _| / _ \ ' \   ┃
;; ┃   |___/\__,_|\__\__,_| |_|\_\___/_| |_|_|_\__,_|_|_/__\__,_|\__|_\___/_||_|  ┃
;; ┃                                                                              ┃
;; ┃                                                                              ┃
;; ┃                                                                              ┃
;; ┃                                                                              ┃
;; ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

(defn product-entity-map
  [sku product]
  (let [{:strs [sku productFamily attributes]} product
        sku (h/conform! :aws.v1.product/sku sku {:product product})
        product-family (h/conform! #{"Enterprise Applications"} productFamily {:product product})
        attributes (h/conform! :aws.v1.workspaces.product/attributes attributes {:product product})]
    (merge {:aws.v1.product/sku                   sku
            :aws.v1.product/product-family        product-family
            :aws.v1.workspaces.product/attributes (first attributes)}
      (second attributes))))

(defn price-entity-map [term sku offer price]
  (let [{:strs [offerTermCode sku effectiveDate priceDimensions]} price
        sku (h/conform! :aws.v1.product/sku sku {:price price})
        offer-term-code (h/conform! :aws.v1.pricing/offer-term-code offerTermCode {:price price})
        effective-date (h/conform! :aws.v1.pricing/effective-date effectiveDate {:price price})
        price-dimensions (into [] (map #(h/conform! :aws.v1.workspaces.pricing/price-dimensions % {:price price})) (vals priceDimensions))]
    (let [term-key (case term "OnDemand" :aws.v1.term/on-demand "Reserved" :aws.v1.term/reserved)]
      {:aws.v1.product/sku sku
       term-key            {:aws.v1.pricing/offer-term-code offer-term-code
                            :aws.v1.pricing/effective-date  effective-date
                            :aws.v1.pricing/dimensions      price-dimensions}})))

;; ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
;; ┃                                                                              ┃
;; ┃                                                                              ┃
;; ┃                                                                              ┃
;; ┃              ___     _    _              ___                                 ┃
;; ┃             | _ \_ _(_)__(_)_ _  __ _   / _ \ _  _ ___ _ _ _  _              ┃
;; ┃             |  _/ '_| / _| | ' \/ _` | | (_) | || / -_) '_| || |             ┃
;; ┃             |_| |_| |_\__|_|_||_\__, |  \__\_\\_,_\___|_|  \_, |             ┃
;; ┃                                 |___/                      |__/              ┃
;; ┃                                                                              ┃
;; ┃                                                                              ┃
;; ┃                                                                              ┃
;; ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

(def cs-workspace-running-mode->aws-workspace-running-mode
  {"AutoStop"       :aws.v1.workspaces.product.running-mode/auto-stop
   "AlwaysOn"       :aws.v1.workspaces.product.running-mode/always-on
   "Not Applicable" :aws.v1.workspaces.product.running-mode/not-applicable})

(def cs-workspace-license->aws-workspace-license
  {"Bring Your Own License" :aws.v1.workspaces.product.license/byol
   "None"                   :aws.v1.workspaces.product.license/none
   "Included"               :aws.v1.workspaces.product.license/included})

(def cs-workspace-os->aws-workspace-os
  {"Windows"      :aws.v1.workspaces.product.operating-system/windows
   "Amazon Linux" :aws.v1.workspaces.product.operating-system/amazon-linux})

(def cs-workspace-billing-method->aws-workspace-billing-method
  {:resource-price.aws-workspaces.billing-method/hourly  :aws.v1.workspaces.product.group/billed-by-hour
   :resource-price.aws-workspaces.billing-method/monthly :aws.v1.workspaces.product.group/billed-by-month})

(defn -resource-price-per-hour*
  [db {:resource-price.aws-workspaces/keys [region running-mode root-volume-gb user-volume-gb bundle operating-system billing-method license]}]
  (let [aws-region region
        aws-running-mode (cs-workspace-running-mode->aws-workspace-running-mode running-mode)
        aws-os (cs-workspace-os->aws-workspace-os operating-system)
        aws-group (cs-workspace-billing-method->aws-workspace-billing-method billing-method)
        aws-license (some-> license cs-workspace-license->aws-workspace-license)
        aws-license (or aws-license (when (= aws-os :aws.v1.workspaces.product.operating-system/windows) :aws.v1.workspaces.product.license/included))
        matches (qbe/q (cond->
                         #:aws.v1.workspaces.product
                                 {:aws.v1.term/on-demand
                                  {:aws.v1.pricing/dimensions
                                   {:aws.v1.pricing.price-per-unit/value '?base-price}}
                                  :attributes       :aws.v1.workspaces.product.attributes/hardware
                                  :location         aws-region
                                  :operating-system aws-os
                                  :bundle           bundle
                                  :running-mode     aws-running-mode
                                  :group            aws-group
                                  :root-volume      '?root-volume
                                  :user-volume      '?user-volume}
                         aws-license
                         (assoc :aws.v1.workspaces.product/license aws-license))
                  db)]
    (if-some [base-price
              (some
                (fn [{:keys [base-price root-volume user-volume]}]
                  (when (and (= root-volume-gb root-volume)
                          (= user-volume-gb user-volume)
                          (pos? base-price))
                    base-price))
                matches)]
      base-price
      (if-some [max-config (last (sort-by (juxt :root-volume :user-volume) matches))]
        (if (= aws-group :aws.v1.workspaces.product.group/billed-by-hour)
          (:base-price max-config)
          (let [[{:keys [root-price user-price] :as f} :as price-list-items]
                (qbe/q [#:aws.v1.workspaces.product
                        {:attributes :aws.v1.workspaces.product.attributes/storage
                         :location   aws-region
                         :license    :aws.v1.workspaces.product.license/root-volume-usage
                         :aws.v1.term/on-demand
                         {:aws.v1.pricing/dimensions
                          {:aws.v1.pricing.price-per-unit/value '?root-price}}}
                        #:aws.v1.workspaces.product
                                {:attributes :aws.v1.workspaces.product.attributes/storage
                                 :location   aws-region
                                 :license    :aws.v1.workspaces.product.license/user-volume-usage
                                 :aws.v1.term/on-demand
                                 {:aws.v1.pricing/dimensions
                                  {:aws.v1.pricing.price-per-unit/value '?user-price}}}]
                  db)]
            (case (bounded-count 2 price-list-items)
              0 (ak/not-found "No matching prices found.")
              1 (+ (:base-price max-config)
                  (* user-price (- user-volume-gb (:user-volume max-config)))
                  (* root-price (- root-volume-gb (:root-volume max-config))))
              (ak/incorrect (format "Too many resource prices found (count: %s)" (count price-list-items))
                {:prices price-list-items}))))
        (ak/not-found "No matching prices found.")))))
