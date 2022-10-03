(ns computesoftware.csp-pricing.azure.vm.license
  (:require
    [clojure.spec.alpha :as s]
    [computesoftware.csp-pricing.azure.common :as common]
    [computesoftware.csp-pricing.impl.spec-helpers :as h]))

(def schema
  [#:db{:ident       :cs.azure.vm.license/name,
        :valueType   :db.type/string,
        :cardinality :db.cardinality/one}
   #:db{:ident       :cs.azure.vm.license/vcpus-min,
        :valueType   :db.type/long,
        :cardinality :db.cardinality/one}
   #:db{:ident       :cs.azure.vm.license/vcpus-max,
        :valueType   :db.type/long,
        :cardinality :db.cardinality/one}])

(def meter-spec-map
  (assoc common/baseline
    "MeterCategory" [:azure.meter/category #{"Virtual Machines Licenses"}]
    "IncludedQuantity" [:azure.meter/included-quantity {0 0.0 0.0 0.0}]))

(s/def :azure.vm.license/meter
  (h/strictly-conform-json-object2
    meter-spec meter-spec-map))

;; These licenses comes from the Commerce API but we cleaned data by
;; reading the official documentation. Some data were found in the
;; Commerce API but not the documentation and vice-versa.
(def supported-license?
  #{"Azure Red Hat OpenShift Compute Optimized v2"
    "Azure Red Hat OpenShift General Purpose v3"
    "Azure Red Hat OpenShift Memory Optimized v3"
    "BizTalk Server Enterprise"
    "BizTalk Server Standard"
    "Canonical Ubuntu Linux Premium"
    #_"Canonical Ubuntu Linux Standard"                     ; A10/A11 only
    #_"RHEL Pre-Pay"                                        ; price is 0
    "RHEL for SAP Business Applications"
    "RHEL for SAP HANA"
    "Red Hat Enterprise Linux"
    "Red Hat Enterprise Linux for SAP with HA"
    "Red Hat Enterprise Linux with HA"
    "SQL Server"
    #_"SQL Server Azure Hybrid Benefit"                     ; price is 0
    "SQL Server Enterprise"
    "SQL Server Enterprise Red Hat Enterprise Linux"
    "SQL Server Enterprise SLES"
    "SQL Server Linux Enterprise"
    "SQL Server Linux Standard"
    "SQL Server Linux Web"
    "SQL Server Standard"
    "SQL Server Standard Red Hat Enterprise Linux"
    "SQL Server Standard SLES"
    "SQL Server Web"
    "SQL Server Web Red Hat Enterprise Linux"
    "SQL Server Web SLES"
    "SUSE Linux Enterprise Server"
    "SUSE Linux Enterprise Server Basic"
    "SUSE Linux Enterprise Server Priority"
    "SUSE Linux Enterprise Server Standard"
    "SUSE Linux Enterprise Server for HPC Priority"
    "SUSE Linux Enterprise Server for HPC Standard"
    "SUSE Linux Enterprise Server for SAP Priority"
    "Ubuntu Advantage"})

(defmacro ^:private else->> [& forms] `(->> ~@(reverse forms)))

(defn assoc-synthetic-fields [{:azure.meter/keys [name sub-category] :as meter}]
  ;; filter on license name (well sub-category): don't inject synthetic
  ;; fields on unsuppoted licenses
  (else->>
    (if-not (supported-license? sub-category) meter)
    (if (#{"BYOS License" "Shared vCPU VM License" "XS vCPU VM Support" "1 vCPU VM BYOS License"} name) meter)
    (if-some [[_ from to and-beyond] (re-matches #"(\d+)(?:-(\d+)|(\+))? vCPU VM (?:Support|License)" name)]
      (let [from (Long/parseLong from)
            to (cond to (Long/parseLong to) and-beyond Long/MAX_VALUE :else from)]
        (assoc meter
          :cs.azure.vm.license/name sub-category
          :cs.azure.vm.license/vcpus-min from
          :cs.azure.vm.license/vcpus-max to)))
    (if-some [[_ level min-fee] (re-matches #"(Essential|Standard|Advanced) VM Support( Minimum Fee)?" name)]
      (if min-fee
        meter
        (assoc meter
          :cs.azure.vm.license/name (str sub-category " " level)
          :cs.azure.vm.license/vcpus-min 0
          :cs.azure.vm.license/vcpus-max Long/MAX_VALUE)))
    (throw (ex-info "Can't parse license applicability" meter))))

(defn do-meter
  [meter]
  (let [meter-c (s/conform :azure.vm.license/meter meter)]
    (if (= ::s/invalid meter-c)
      (throw (ex-info "Invalid meter"
               {:meter meter
                :spec  :azure.vm.license/meter}))
      (assoc-synthetic-fields meter-c))))
