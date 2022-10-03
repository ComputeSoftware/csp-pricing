(ns computesoftware.csp-pricing.azure.vm
  (:require
    [clojure.spec.alpha :as s]
    [computesoftware.csp-pricing.azure.common :as common]
    [computesoftware.csp-pricing.impl.spec-helpers :as h]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [clojure.string :as string]))

(def schema
  [#:db{:ident       :cs.azure.vm/tier,
        :valueType   :db.type/keyword,
        :cardinality :db.cardinality/one}
   #:db{:ident       :cs.azure.vm/low-priority?,
        :valueType   :db.type/boolean,
        :cardinality :db.cardinality/one}
   #:db{:ident       :cs.azure.vm/promo?,
        :valueType   :db.type/boolean,
        :cardinality :db.cardinality/one}
   #:db{:ident       :cs.azure.vm/os,
        :valueType   :db.type/keyword,
        :cardinality :db.cardinality/one}
   #:db{:ident       :cs.azure.vm/series,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}
   #:db{:ident       :cs.azure.vm/instance-type,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/many}
   #:db{:ident       :cs.azure.vm/ram,
        :valueType   :db.type/double,
        :cardinality :db.cardinality/one}
   #:db{:ident       :cs.azure.vm/vcpus,
        :valueType   :db.type/long,
        :cardinality :db.cardinality/one}])

;; how to produce azure/vm/instances-select.edn
;; 1. Go to https://azure.microsoft.com/en-us/pricing/calculator/
;; 2. Select "West US 2" as Region
;; 3. With the DOM inspector select the <select>
;; 4. In the console, run:
;;    console.log(JSON.stringify(Array.prototype.map.call($0.options, e => e.textContent)))
;; 5. Copy and pbpaste > azure/vm/instances-select.edn

(def scraped-vms-specs
  (with-open [r (io/reader (io/resource "azure/vm/instances-select.edn"))
              r (clojure.lang.LineNumberingPushbackReader. r)]
    (into {}
      (keep
        (fn [option]
          (if-some [[_ type vcpus vcpu-or-core ram] (re-matches #"([^:]+): ([.0-9]+) (vCPU|Cores)\(s\), ([.0-9]+) GB RAM,.*" option)]
            [type #:cs.azure.vm{:vcpus (Long/parseLong vcpus) :ram (Double/parseDouble ram)}]
            ;; @NOTE : when it fails, it's because the scraped format
            ;; has been changed by MSFT... we have to analyze why it
            ;; failed and change the regular expression above.
            (throw (ex-info "scraped-vms-specs failed" option)))))
      (edn/read r))))

(def anomalous-vms-specs
  "Correct values for missing or incorrect data."
  {"HB60rs"     #:cs.azure.vm{:vcpus 60 :ram 240.0}
   "HC44rs"     #:cs.azure.vm{:vcpus 44 :ram 352.0}
   "HB120rs_v2" #:cs.azure.vm{:vcpus 120 :ram 480.0}
   "M128-64ms"  #:cs.azure.vm{:vcpus 64 :ram 3892.0}})

(defn vm-specs [type vms-specs]
  (or
    ; explicit fixes
    (anomalous-vms-specs type)
    ; official data from api
    (vms-specs type)
    ; manually scraped data
    (scraped-vms-specs type)))

(def meter-spec-map
  (assoc common/baseline
    "MeterCategory" [:azure.meter/category #{"Virtual Machines"}]))

(s/def :azure.vm/meter
  (h/strictly-conform-json-object2
    meter-spec meter-spec-map))

(defn assoc-synthetic-fields [meter vms-specs]
  (let [[_ types tier tag] (re-matches #"(.*?)(?: (Low Priority))?(?: - (.*))?" (:azure.meter/name meter))
        types (str/split types #"/")
        ;; specs are believed to be homogeneous across types in a single meter
        specs (vm-specs (first types) vms-specs)
        promo? (str/includes? (:azure.meter/sub-category meter) "Promo")
        [parsed-sub-category series basic-tier windows] (re-matches #"(.*) Series( Basic)?( Windows)?" (:azure.meter/sub-category meter))
        vm-tier (if basic-tier :basic :standard)]
    (-> meter
      (assoc
        :cs.azure.vm/tier vm-tier
        :cs.azure.vm/promo? promo?
        :cs.azure.vm/low-priority? (= "Low Priority" tier)
        :cs.azure.vm/instance-type types)
      (cond->                                               ;; to avoid associating nils on special meters like Compute & Dedicated Host Reservation
        parsed-sub-category
        (assoc
          :cs.azure.vm/os (if windows :windows :linux)
          :cs.azure.vm/series series))
      (into (some-> specs (update :cs.azure.vm/vcpus long))))))

;; TODO: name -> Standard "" or Low Priority + split on sizes (/-separated)
;; category -> OS

;; Basic + Low Priority -> ignored

(defn do-meter
  [meter vms-specs]
  (let [meter-c (h/conform! :azure.vm/meter meter {})]
    (assoc-synthetic-fields meter-c vms-specs)))

(comment

  (qbe/q
    '#:cs.azure.vm
            {:azure.meter/rates  #:azure.meter.rate{:threshold ?t :rate ?r}
             :series             "BS"
             :os                 :windows
             :azure.meter/region "US West"
             :instance-type      "B1s"
             :tier               :standard} db)

  [{:r 0.0164, :t 0.0}] 3

  (qbe/q
    '#:cs.azure.vm
            {:azure.meter/rates  #:azure.meter.rate{:threshold ?t :rate ?r}
             :azure.meter/region "US West 2"
             :instance-type      "D2 v3"
             :ram                ?ram
             :vcpus              ?vcpus
             :os                 :windows
             :series             ?series
             :tier               :standard} db)
  [{:vcpus 2, :series "Dv3/DSv3", :ram 8.0, :r 0.188, :t 0.0}]
  (* 730 0.188) 137.24

  (d/q '[:find ?n
         :where
         [?l :cs.azure.vm.license/name ?n]
         #_#_#_[?l :cs.azure.vm.license/vcpus-min ?vmin]
                 [?l :cs.azure.vm.license/vcpus-max ?vmax]
                 [(<= ?vmin 2)]]
    db)

  [["SQL Server Enterprise"]
   ["SQL Server Standard Red Hat Enterprise Linux"]
   ["SQL Server Linux Web"]
   ["SQL Server Enterprise Red Hat Enterprise Linux"]
   ["SUSE Linux Enterprise Server for HPC Priority"]
   ["Red Hat Enterprise Linux with HA"]
   ["SQL Server Linux Enterprise"]
   ["Red Hat Enterprise Linux for SAP with HA"]
   ["SQL Server Web SLES"]
   ["SQL Server Enterprise SLES"]
   ["Ubuntu Advantage Standard"]
   ["BizTalk Server Standard"]
   ["SQL Server Web"]
   ["RHEL for SAP HANA"]
   ["SQL Server Linux Standard"]
   ["SQL Server Standard"]
   ["SUSE Linux Enterprise Server Standard"]
   ["SUSE Linux Enterprise Server for SAP Priority"]
   ["Canonical Ubuntu Linux Premium"]
   ["SUSE Linux Enterprise Server for HPC Standard"]
   ["BizTalk Server Enterprise"]
   ["Ubuntu Advantage Advanced"]
   ["Red Hat Enterprise Linux"]
   ["SQL Server Web Red Hat Enterprise Linux"]
   ["Ubuntu Advantage Essential"]
   ["SQL Server Standard SLES"]
   ["SUSE Linux Enterprise Server Priority"]
   ["SUSE Linux Enterprise Server Basic"]
   ["RHEL for SAP Business Applications"]]

  (d/q '[:find (pull ?l [*])
         :where
         [?l :cs.azure.vm.license/name "SQL Server Web"]
         [?l :cs.azure.vm.license/vcpus-min ?vmin]
         [?l :cs.azure.vm.license/vcpus-max ?vmax]
         [(<= ?vmin 2)]
         [(<= 2 ?vmax)]]
    db)
  [[{:cs.azure.vm.license/name      "SQL Server Web",
     :azure.meter/sub-category      "SQL Server Web",
     :azure.meter/effective-date    #inst "2014-08-01T00:00:00.000-00:00",
     :azure.meter/included-quantity 0.0,
     :azure.meter/region            "",
     :azure.meter/name              "1-4 vCPU VM License",
     :azure.meter/unit              "1 Hour",
     :azure.meter/category          "Virtual Machines Licenses",
     :azure.meter/id                "8e9af6db-7104-4f1a-830e-93eabb955444",
     :cs.azure.vm.license/vcpus-max 4,
     :cs.azure.vm.license/vcpus-min 1,
     :azure.meter/rates
     [{:db/id                      17592195517912,
       :azure.meter.rate/threshold 0.0,
       :azure.meter.rate/rate      0.032}],
     :db/id                         17592195517911}]]

  (* 730 0.032)
  (qbe/q
    '[:find ?monthly ?monthly-license ?monthly-vm
      :where
      #:cs.azure.vm
              {:azure.meter/rates  #:azure.meter.rate{:threshold ?t :rate ?r}
               :azure.meter/region "US West 2"
               :instance-type      "M32ms"
               :ram                ?ram
               :vcpus              ?vcpus
               :os                 :linux
               :series             ?series
               :tier               :standard}
      #:cs.azure.vm.license
              {:name              "SUSE Linux Enterprise Server Standard"
               :azure.meter/rates #:azure.meter.rate{:rate ?rlicense}
               :vcpus-min         ?vmin
               :vcpus-max         ?vmax}
      [(<= ?vmin ?vcpus)]
      [(<= ?vcpus ?vmax)]
      [(* 730 ?r) ?monthly-vm]
      [(* 730 ?rlicense) ?monthly-license]
      [(+ ?monthly-license ?monthly-vm) ?monthly]] db)
  [[4596.08 109.5 4486.58]]

  (qbe/q
    '#:cs.azure.vm
            {:azure.meter/rates  #:azure.meter.rate{:threshold ?t :rate ?r}
             :azure.meter/region "US West"
             :instance-type      "E8-2ds v4"
             :ram                ?ram
             :vcpus              ?vcpus
             :os                 ?os
             :series             ?series
             :tier               :standard} db)

  [{:os :linux, :vcpus 2, :ram 64.0, :r 0.648, :t 0.0} {:os :windows, :vcpus 2, :ram 64.0, :r 1.016, :t 0.0}]

  (qbe/q
    '#:cs.azure.vm
            {:azure.meter/rates  #:azure.meter.rate{:threshold ?t :rate ?r}
             :azure.meter/region "US West"
             :instance-type      ?type
             :ram                64.0
             :vcpus              8
             :os                 ?os
             :tier               :standard} db)
  [{:os :linux, :type "E8d v4", :r 0.648, :t 0.0}
   {:os :windows, :type "A8m v2", :r 0.9, :t 0.0}
   {:os :windows, :type "E8as v4", :r 0.928, :t 0.0} {:os :linux, :type "E8 v3", :r 0.56, :t 0.0} {:os :windows, :type "E8 v3", :r 0.928, :t 0.0} {:os :linux, :type "E8as v4", :r 0.56, :t 0.0} {:os :windows, :type "E8 v4", :r 0.928, :t 0.0} {:os :linux, :type "L8s", :r 0.688, :t 0.0} {:os :linux, :type "L8s v2", :r 0.688, :t 0.0} {:os :linux, :type "E8s v4", :r 0.56, :t 0.0} {:os :windows, :type "E8d v4", :r 1.016, :t 0.0} {:os :linux, :type "E8s v3", :r 0.56, :t 0.0} {:os :windows, :type "L8s v2", :r 1.056, :t 0.0} {:os :linux, :type "E8 v4", :r 0.56, :t 0.0} {:os :linux, :type "E8ds v4", :r 0.648, :t 0.0} {:os :windows, :type "L8s", :r 1.056, :t 0.0} {:os :linux, :type "A8m v2", :r 0.594, :t 0.0} {:os :windows, :type "E8ds v4", :r 1.016, :t 0.0} {:os :linux, :type "E8a v4", :r 0.56, :t 0.0} {:os :windows, :type "E8s v4", :r 0.928, :t 0.0} {:os :windows, :type "E8s v3", :r 0.928, :t 0.0} {:os :windows, :type "E8a v4", :r 0.928, :t 0.0}])


