(ns computesoftware.csp-pricing.aws.ec2
  (:require
    [clojure.spec.alpha :as s]
    [computesoftware.csp-pricing.aws.common]
    [computesoftware.csp-pricing.impl.spec-helpers :as h]
    [datomic.client.api :as d]
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

(s/def :aws.v1.ec2.product/instance-family
  #{"Compute optimized"
    "FPGA Instances"
    "GPU instance"
    "General purpose"
    "Machine Learning ASIC Instances"
    "Media Accelerator Instances"
    "Memory optimized"
    "Micro instances"
    "Storage optimized"})

(s/def :aws.v1.ec2.product/vcpu #{"1" "2" "4" "8" "12" "16" "24" "32" "36" "40" "48" "64" "72" "96" "128" "192" "224" "448"})

(s/def :aws.v1.ec2.product/physical-processor string?)

(s/def :aws.v1.ec2.product/network-performance #{"10 Gigabit"
                                                 "100 Gigabit"
                                                 "12 Gigabit"
                                                 "15 Gigabit"
                                                 "20 Gigabit"
                                                 "25 Gigabit"
                                                 "40 Gigabit"
                                                 "50 Gigabit"
                                                 "75 Gigabit"
                                                 "400 Gigabit"
                                                 "3125 Megabit"
                                                 "6250 Megabit"
                                                 "12500 Megabit"
                                                 "18750 Megabit"
                                                 "22500 Megabit"
                                                 "25000 Megabit"
                                                 "37500 Megabit"
                                                 "50000 Megabit"
                                                 "75000 Megabit"
                                                 "High"
                                                 "Low"
                                                 "Low to Moderate"
                                                 "Moderate"
                                                 "NA"
                                                 "Up to 12500 Megabit"
                                                 "Up to 5 Gigabit"
                                                 "Up to 10 Gigabit"
                                                 "Up to 12 Gigabit"
                                                 "Up to 15 Gigabit"
                                                 "Up to 25 Gigabit"
                                                 "30 Gigabit"
                                                 "Very Low"})

(s/def :aws.v1.ec2.product/processor-architecture #{"32-bit or 64-bit" "64-bit"})

(s/def :aws.v1.ec2.product/license-model #{"Bring your own license" "No License required" "NA"})

(s/def :aws.v1.ec2.product/usage-type string?)

(s/def :aws.v1.ec2.product/operation string?)

(s/def :aws.v1.ec2.product/capacity-status string?)

(s/def :aws.v1.ec2.product/ecu #{"Variable" "NA"
                                 "1" "2" "3" "4" "5" "6.5" "7" "8" "10" "12" "13" "14"
                                 "16" "19" "20" "23" "26" "27" "28" "31" "35" "37" "39"
                                 "45" "47" "52" "53" "53.5" "55" "56" "58" "62" "64" "70"
                                 "73" "88" "91" "97" "104" "108" "116" "124.5" "128" "132"
                                 "139" "168" "174.5" "179" "188" "201" "208" "235" "256"
                                 "271" "281" "337" "340" "345" "347" "349" "375"})

(s/def :aws.v1.ec2.product/normalization-size-factor
  #{"NA"
    "0.25"
    "0.5"
    "1"
    "2"
    "4"
    "8"
    "16"
    "24"
    "32"
    "48"
    "64"
    "72"
    "80"
    "96"
    "128"
    "144"
    "192"
    "256"
    ;; Added on 2021-08-20 aws pricing data update. I do expect that these values
    ;; should be removed in the future since it does not make sense.
    "13.97113"
    "19.97394"
    ;; Added on 2021-12-09.
    "384"
    "1.204771372"
    "1.323809524"
    "1.614314115"
    "1.971428571"
    "2.433399602"
    "3.266666667"
    "4.071570577"
    "5.638170974"
    "6.533333333"
    "8.095427435"
    "16.190854871"
    ;; Added 2022-02-21
    "448"})

;; optional
(s/def :aws.v1.ec2.product/from-location-type #{"AWS Region" "Other"})

(s/def :aws.v1.ec2.product/gpu-memory #{"4 GiB" "2 GiB" "1 GiB" "8 GiB"})

(s/def :aws.v1.ec2.product/group #{"CarrierIP:Address"
                                   "CarrierIP:AdditionalAddress"
                                   "CarrierIP:Remap"
                                   "EBS I/O Requests"
                                   "EBS IOPS"
                                   "EBS IOPS Tier 2"
                                   "EBS IOPS Tier 3"
                                   "EBS direct APIs:Get Requests"
                                   "EBS direct APIs:List Requests"
                                   "EBS direct APIs:Put Requests"
                                   "EBS Throughput"
                                   "EC2-Dedicated Usage"
                                   "ELB:Balancer"
                                   "ELB:Balancing"
                                   "ElasticIP:AdditionalAddress"
                                   "ElasticIP:Address"
                                   "ElasticIP:Remap"
                                   "NGW:NatGateway"})

(s/def :aws.v1.ec2.product/max-volume-size #{"1 TiB" "16 TiB"})

(s/def :aws.v1.ec2.product/group-description string?)

(s/def :aws.v1.ec2.product/storage-media #{"Amazon S3"
                                           "Amazon S3 Outpost"
                                           "HDD-backed"
                                           "SSD-backed"})

(s/def :aws.v1.ec2.product/to-location #{"EU (Ireland)" "AWS GovCloud (US-West)" "EU (Frankfurt)"
                                         "Asia Pacific (Sydney)" "US West (N. California)"
                                         "Asia Pacific (Singapore)" "South America (Sao Paulo)"
                                         "Asia Pacific (Tokyo)" "Asia Pacific (Seoul)" "US West (Oregon)"
                                         "US East (N. Virginia)" "External"})

(s/def :aws.v1.ec2.product/volume-type #{"General Purpose" "Cold HDD" "Magnetic" "Provisioned IOPS"
                                         "Throughput Optimized HDD"})

(s/def :aws.v1.ec2.product/instance #{"T2" "T3" "T3A" "T4G"})

(s/def :aws.v1.ec2.product/transfer-type #{"InterRegion Outbound" "IntraRegion" "InterRegion Inbound"
                                           "AWS Outbound" "AWS Inbound"})

(s/def :aws.v1.ec2.product/resource-type #{"Get:Snapshot" "List:Snapshot" "Put:Snapshot"})

(s/def :aws.v1.ec2.product/from-location #{"EU (Ireland)" "AWS GovCloud (US-West)" "EU (Frankfurt)"
                                           "Asia Pacific (Sydney)" "US West (N. California)"
                                           "Asia Pacific (Singapore)" "South America (Sao Paulo)"
                                           "Asia Pacific (Tokyo)" "Asia Pacific (Seoul)" "US West (Oregon)"
                                           "US East (N. Virginia)" "External"})

(s/def :aws.v1.ec2.product/volume-api-name #{"standard" "sc1" "io1" "io2" "gp2" "gp3" "st1"})

;; Best place to find these seems to be in AWS console:
;; https://us-west-2.console.aws.amazon.com/ec2/v2/home?region=us-west-2#Settings:tab=zones
(def region-name->code
  {"AWS GovCloud (US-East)"                     "us-gov-east-1",
   "AWS GovCloud (US-West)"                     "us-gov-west-1",
   "Africa (Cape Town)"                         "af-south-1",
   "Asia Pacific (Hong Kong)"                   "ap-east-1",
   "Asia Pacific (Jakarta)"                     "ap-southeast-3"
   "Asia Pacific (KDDI) - Osaka"                "Asia Pacific (KDDI) - Osaka"
   "Asia Pacific (KDDI) - Tokyo"                "Asia Pacific (KDDI) - Tokyo"
   "Asia Pacific (Mumbai)"                      "ap-south-1",
   "Asia Pacific (Osaka)"                       "ap-northeast-3"
   "Asia Pacific (Osaka-Local)"                 "ap-northeast-3",
   "Asia Pacific (Seoul)"                       "ap-northeast-2",
   "Asia Pacific (Singapore)"                   "ap-southeast-1",
   "Asia Pacific (SKT) - Daejeon"               "ap-northeast-2-wl1-cjj-wlz-1"
   "Asia Pacific (SKT) - Seoul"                 "ap-northeast-2-wl1-sel-wlz-1"
   "Asia Pacific (Sydney)"                      "ap-southeast-2",
   "Asia Pacific (Tokyo)"                       "ap-northeast-1",
   "Canada (BELL) - Toronto"                    "ca-central-1-wl1-yto-wlz-1"
   "Canada (Central)"                           "ca-central-1",
   "EU (Frankfurt)"                             "eu-central-1",
   "EU (Ireland)"                               "eu-west-1",
   "EU (London)"                                "eu-west-2",
   "EU (Milan)"                                 "eu-south-1",
   "EU (Paris)"                                 "eu-west-3",
   "EU (Stockholm)"                             "eu-north-1",
   "EU West (Vodafone) - London"                "eu-west-2-wl1-lon-wlz-1"
   "Europe (Vodafone) - London"                 "eu-west-2-wl1-lon-wlz-1"
   "Europe (Vodafone) - Berlin"                 "eu-central-1-wl1-ber1"
   "Europe (Vodafone) - Dortmund"               "eu-central-1-wl1-dtm1"
   "Europe (Vodafone) - Munich"                 "eu-central-1-wl1-muc1"
   "Middle East (Bahrain)"                      "me-south-1",
   "South America (Sao Paulo)"                  "sa-east-1",
   "US East (Atlanta)"                          "us-east-1-atl-1"
   "US East (Boston)"                           "us-east-1-bos-1"
   "US East (Chicago)"                          "us-east-1-chi-1"
   "US East (Dallas)"                           "us-east-1-dfw-1"
   "US East (Houston)"                          "us-east-1-iah-1"
   "US East (Kansas City 2)"                    "us-east-1-mci-1"
   "US East (Miami)"                            "us-east-1-mia-1"
   "US East (Minneapolis)"                      "us-east-1-msp-1"
   "US East (N. Virginia)"                      "us-east-1",
   "US East (Ohio)"                             "us-east-2",
   "US East (Philadelphia)"                     "us-east-1-phl-1"
   "US West (Seattle)"                          "us-west-2-sea-1"
   "US East (Verizon) - Atlanta"                "us-east-1-wl1-atl-wlz-1"
   "US East (Verizon) - Charlotte"              "us-east-1-wl1-clt1"
   "US East (Verizon) - Boston"                 "us-east-1-wl1-bos-wlz-1"
   "US East (Verizon) - Chicago"                "us-east-1-wl1-chi-wlz-1"
   "US East (Verizon) - Dallas"                 "us-east-1-wl1-dfw-wlz-1"
   "US East (Verizon) - Detroit"                "us-east-1-wl1-dtw1"
   "US East (Verizon) - Houston"                "us-east-1-wl1-iah-wlz-1"
   "US West (Verizon) - Las Vegas"              "us-west-2-wl1-las-wlz-1"
   "US West (Verizon) - Los Angeles"            "us-west-2-wl1-lax1"
   "US East (Verizon) - Minneapolis"            "us-east-1-wl1-msp1"
   "US East (Verizon) - Miami"                  "us-east-1-wl1-mia-wlz-1"
   "US East (Verizon) - Nashville"              "us-east-1-wl1-bna-wlz-1"
   "US East (Verizon) - New York"               "us-east-1-wl1-nyc-wlz-1"
   "US East (Verizon) - Tampa"                  "us-east-1-wl1-tpa-wlz-1"
   "US East (Verizon) - Washington DC"          "us-east-1-wl1-was-wlz-1"
   "US East (New York City)"                    "us-east-1-nyc-1a"
   "US West (Portland)"                         "us-west-2-pdx-1a"
   "US West (Las Vegas)"                        "us-west-2-las-1a"
   "US West (Denver)"                           "us-west-2-den-1"
   "US West (Phoenix)"                          "us-west-2-phx-1"
   "US West (Los Angeles)"                      "us-west-2-lax-1",
   "US West (N. California)"                    "us-west-1",
   "US West (Oregon)"                           "us-west-2"
   "US West (Verizon) - Denver"                 "us-west-2-wl1-den-wlz-1"
   "US West (Verizon) - Phoenix"                "us-west-2-wl1-phx-wlz-1"
   "US West (Verizon) - San Francisco Bay Area" "us-west-2-wl1-sfo-wlz-1"
   "US West (Verizon) - Seattle"                "us-west-2-wl1-sea-wlz-1"})

(s/def :aws.v1.ec2.product/attributes
  (h/strictly-conform-json-object2
    ec2-product-attributes
    {"servicename"                 [:aws.v1.ec2/service-name #{"Amazon Elastic Compute Cloud"}]
     "servicecode"                 [:aws.v1.product/service-code #{"AmazonEC2"}]
     "operation"                   [:aws.v1.ec2.product/operation :aws.v1.ec2.product/operation]
     "usagetype"                   [:aws.v1.ec2.product/usage-type :aws.v1.ec2.product/usage-type]
     "location"                    [:aws.v1.ec2.product/location region-name->code :opt]
     "regionCode"                  [;; region code, like 'us-east-1'
                                    ;; optional, not present in every element
                                    :aws.v1.ec2.product/region-code string? :opt]
     "toRegionCode"                [;; region code, like 'us-east-1'
                                    ;; optional, not present in every element
                                    :aws.v1.ec2.product/to-region-code string? :opt]
     "fromRegionCode"              [;; region code, like 'us-east-1'
                                    ;; optional, not present in every element
                                    :aws.v1.ec2.product/from-region-code string? :opt]
     "snapshotarchivefeetype"      [:aws.v1.ec2.product/snapshot-archive-fee-type string? :opt]
     "locationType"                [:aws.v1.ec2/location-type
                                    #{"AWS Local Zone"
                                      "AWS Outposts"
                                      "AWS Region"
                                      "AWS Wavelength Zone"} :opt]
     "instanceType"                [:aws.v1.ec2/instance-type string? :opt]
     "currentGeneration"           [:aws.v1.ec2.product/current-generation #{"Yes" "No"} :opt]
     "instanceFamily"              [:aws.v1.ec2.product/instance-family :aws.v1.ec2.product/instance-family :opt]
     "vcpu"                        [:aws.v1.ec2.product/vcpu :aws.v1.ec2.product/vcpu :opt]
     "physicalProcessor"           [:aws.v1.ec2.product/physical-processor string? :opt]
     "clockSpeed"                  [:aws.v1.ec2.product/clock-speed string? :opt]
     "memory"                      [:aws.v1.ec2.product/memory string? :opt]
     "storage"                     [:aws.v1.ec2.product/storage string? :opt]
     "networkPerformance"          [:aws.v1.ec2.product/network-performance :aws.v1.ec2.product/network-performance :opt]
     "processorArchitecture"       [:aws.v1.ec2.product/processor-architecture :aws.v1.ec2.product/processor-architecture :opt]
     "tenancy"                     [:aws.v1.ec2.product/tenancy {"Reserved"  :aws.v1.ec2.product.tenancy/reserved,
                                                                 "Dedicated" :aws.v1.ec2.product.tenancy/dedicated,
                                                                 "Shared"    :aws.v1.ec2.product.tenancy/shared,
                                                                 "Host"      :aws.v1.ec2.product.tenancy/host,
                                                                 "NA"        :aws.v1.ec2.product.tenancy/na} :opt]
     "operatingSystem"             [:aws.v1.ec2.product/operating-system {"Linux"                            :aws.v1.ec2.product.operating-system/linux,
                                                                          "Windows"                          :aws.v1.ec2.product.operating-system/windows,
                                                                          "SUSE"                             :aws.v1.ec2.product.operating-system/suse,
                                                                          "Red Hat Enterprise Linux with HA" :aws.v1.ec2.product.operating-system/red-hat-ha,
                                                                          "RHEL"                             :aws.v1.ec2.product.operating-system/rhel,
                                                                          "NA"                               :aws.v1.ec2.product.operating-system/na} :opt]
     "licenseModel"                [:aws.v1.ec2.product/license-model :aws.v1.ec2.product/license-model :opt]
     "capacitystatus"              [:aws.v1.ec2.product/capacity-status :aws.v1.ec2.product/capacity-status :opt]
     "dedicatedEbsThroughput"      [:aws.v1.ec2.product/dedicated-ebs-throughput string? :opt]
     "ecu"                         [:aws.v1.ec2.product/ecu :aws.v1.ec2.product/ecu :opt]
     "enhancedNetworkingSupported" [:aws.v1.ec2.product/enhanced-networking-supported #{"Yes" "No"} :opt]
     "instancesku"                 [:aws.v1.ec2.product/instance-sku string? :opt]
     "intelAvxAvailable"           [:aws.v1.ec2.product/intel-avx-available #{"Yes" "No"} :opt]
     "intelAvx2Available"          [:aws.v1.ec2.product/intel-avx2-available #{"Yes" "No"} :opt]
     "intelTurboAvailable"         [:aws.v1.ec2.product/intel-turbo-available #{"Yes" "No"} :opt]
     "normalizationSizeFactor"     [:aws.v1.ec2.product/normalization-size-factor :aws.v1.ec2.product/normalization-size-factor :opt]
     "preInstalledSw"              [:aws.v1.ec2.product/pre-installed-sw {"SQL Ent" :aws.v1.ec2.product.pre-installed-sw/sql-ent
                                                                          "SQL Web" :aws.v1.ec2.product.pre-installed-sw/sql-web
                                                                          "SQL Std" :aws.v1.ec2.product.pre-installed-sw/sql-std
                                                                          "NA"      :aws.v1.ec2.product.pre-installed-sw/na} :opt]
     "processorFeatures"           [:aws.v1.ec2.product/processor-features string? :opt]
     "fromLocationType"            [:aws.v1.ec2.product/from-location-type :aws.v1.ec2.product/from-location-type :opt]
     "instanceCapacity4xlarge"     [:aws.v1.ec2.product/instance-capacity-4xlarge #"\d+" :opt]
     "instanceCapacityMetal"       [:aws.v1.ec2.product/instance-capacity-metal #"\d+" :opt]
     "instanceCapacity24xlarge"    [:aws.v1.ec2.product/instance-capacity-24xlarge #"\d+" :opt]
     "instanceCapacity10xlarge"    [:aws.v1.ec2.product/instance-capacity-10xlarge #"\d+" :opt]
     "instanceCapacity9xlarge"     [:aws.v1.ec2.product/instance-capacity-9xlarge #"\d+" :opt]
     "instanceCapacityLarge"       [:aws.v1.ec2.product/instance-capacity-large #"\d+" :opt]
     "instanceCapacity32xlarge"    [:aws.v1.ec2.product/instance-capacity-32xlarge #"\d+" :opt]
     "instanceCapacity18xlarge"    [:aws.v1.ec2.product/instance-capacity-18xlarge #"\d+" :opt]
     "instanceCapacity2xlarge"     [:aws.v1.ec2.product/instance-capacity-2xlarge #"\d+" :opt]
     "instanceCapacity12xlarge"    [:aws.v1.ec2.product/instance-capacity-12xlarge #"\d+" :opt]
     "instanceCapacity16xlarge"    [:aws.v1.ec2.product/instance-capacity-16xlarge #"\d+" :opt]
     "instanceCapacity8xlarge"     [:aws.v1.ec2.product/instance-capacity-8xlarge #"\d+" :opt]
     "instanceCapacityXlarge"      [:aws.v1.ec2.product/instance-capacity-xlarge #"\d+" :opt]
     "instanceCapacityMedium"      [:aws.v1.ec2.product/instance-capacity-medium #"\d+" :opt]
     "physicalCores"               [:aws.v1.ec2.product/physical-cores #"\d+" :opt]
     "gpuMemory"                   [:aws.v1.ec2.product/gpu-memory :aws.v1.ec2.product/gpu-memory :opt]
     "group"                       [:aws.v1.ec2.product/group :aws.v1.ec2.product/group :opt]
     "maxVolumeSize"               [:aws.v1.ec2.product/max-volume-size :aws.v1.ec2.product/max-volume-size :opt]
     "groupDescription"            [:aws.v1.ec2.product/group-description :aws.v1.ec2.product/group-description :opt]
     "storageMedia"                [:aws.v1.ec2.product/storage-media :aws.v1.ec2.product/storage-media :opt]
     "toLocation"                  [:aws.v1.ec2.product/to-location :aws.v1.ec2.product/to-location :opt]
     "maxIopsBurstPerformance"     [:aws.v1.ec2.product/max-iops-burst-performance string? :opt]
     "elasticGraphicsType"         [:aws.v1.ec2.product/elastic-graphics-type string? :opt]
     "productType"                 [:aws.v1.ec2.product/product-type #{"Base"} :opt]
     "ebsOptimized"                [:aws.v1.ec2.product/ebs-optimized #{"Yes"} :opt]
     "volumeType"                  [:aws.v1.ec2.product/volume-type :aws.v1.ec2.product/volume-type :opt]
     "instance"                    [:aws.v1.ec2.product/instance :aws.v1.ec2.product/instance :opt]
     "maxThroughputvolume"         [:aws.v1.ec2.product/max-throughput-volume string? :opt]
     "toLocationType"              [:aws.v1.ec2.product/to-location-type #{"AWS Region" "Other"} :opt]
     "transferType"                [:aws.v1.ec2.product/transfer-type :aws.v1.ec2.product/transfer-type :opt]
     "maxIopsvolume"               [:aws.v1.ec2.product/max-iops-volume string? :opt]
     "gpu"                         [:aws.v1.ec2.product/gpu #{"4" "8" "1" "2" "16"} :opt]
     "resourceType"                [:aws.v1.ec2.product/resource-type :aws.v1.ec2.product/resource-type :opt]
     "fromLocation"                [:aws.v1.ec2.product/from-location :aws.v1.ec2.product/from-location :opt]
     "volumeApiName"               [:aws.v1.ec2.product/volume-api-name :aws.v1.ec2.product/volume-api-name :opt]
     "provisioned"                 [:aws.v1.ec2.product/provisioned #{"Yes" "No"} :opt]
     "availabilityzone"            [nil #{"NA"} :opt]
     "vpcnetworkingsupport"        [nil #{"true" "false"} :opt]
     "classicnetworkingsupport"    [nil #{"true" "false"} :opt]
     "marketoption"                [nil #{"OnDemand"} :opt]}))

(defmulti product-attributes-spec #(get % "servicecode"))

(s/def :aws.v1.ec2.pricing/price-dimensions
  (h/strictly-conform-json-object2
    price-dimensions-spec
    {"rateCode"     [:aws.v1.pricing/rate-code string?]
     "description"  [:aws.v1.pricing/description string?]
     "beginRange"   [nil #{"0" "1" "100" "10240" "153600" "51200"} :opt]
     "endRange"     [nil #{"10240" "51200" "100" "153600" "1" "Inf"} :opt]
     "unit"         [:aws.v1.pricing/unit (s/conformer #(case %
                                                          ("Hrs" "hours" "Hours" "hour") :aws.v1.pricing.unit/hour
                                                          ("GB-Mo" "GB-month") :aws.v1.pricing.unit/gb-month
                                                          "GB" :aws.v1.pricing.unit/gb
                                                          "Count" :aws.v1.pricing.unit/count
                                                          "IOPS-Mo" :aws.v1.pricing.unit/iops-month
                                                          "IOs" :aws.v1.pricing.unit/ios
                                                          "SnapshotAPIUnits" :aws.v1.pricing.unit/snapshot-api-unit
                                                          "Quantity" :aws.v1.pricing.unit/quantity
                                                          "IdleLCU-Hr" :aws.v1.pricing.unit/idle-lcu-hour
                                                          "Requests" :aws.v1.pricing.unit/request
                                                          "Gbps-hrs" :aws.v1.pricing.unit/gbps-hour
                                                          "vCPU-Hours" :aws.v1.pricing.unit/vcpu-hour
                                                          "LCU-Hrs" :aws.v1.pricing.unit/lcu-hour
                                                          "MiBps-Mo" :aws.v1.pricing.unit/mibps-month
                                                          "GiBps-mo" :aws.v1.pricing.unit/gibps-month
                                                          ::s/invalid))]
     "pricePerUnit" [:aws.v1.pricing.price-per-unit/value
                     (s/conformer #(try (Double/parseDouble (get % "USD"))
                                     (catch java.lang.NumberFormatException _ ::s/invalid)))]
     "appliesTo"    [nil (s/coll-of string?)]}))


(def schema
  [#:db{:ident       :aws.v1.ec2.product.usage-type/box-usage
        :valueType   :db.type/boolean
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/location
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}
   #:db{:ident       :aws.v1.ec2.product/region-code
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}
   #:db{:ident       :aws.v1.ec2.product/from-region-code
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}
   #:db{:ident       :aws.v1.ec2.product/to-region-code
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}
   #:db{:ident       :aws.v1.ec2.product/snapshot-archive-fee-type
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}


   #:db{:ident       :aws.v1.ec2/location-type
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2/instance-type
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/current-generation
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-family
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/vcpu
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/physical-processor
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/clock-speed
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/memory
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/storage
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/network-performance
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/processor-architecture
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/tenancy
        :valueType   :db.type/ref
        :cardinality :db.cardinality/one}
   {:db/ident :aws.v1.ec2.product.tenancy/reserved}
   {:db/ident :aws.v1.ec2.product.tenancy/dedicated}
   {:db/ident :aws.v1.ec2.product.tenancy/shared}
   {:db/ident :aws.v1.ec2.product.tenancy/host}
   {:db/ident :aws.v1.ec2.product.tenancy/na}

   #:db{:ident       :aws.v1.ec2.product/operating-system
        :valueType   :db.type/ref
        :cardinality :db.cardinality/one}
   {:db/ident :aws.v1.ec2.product.operating-system/windows}
   {:db/ident :aws.v1.ec2.product.operating-system/linux}
   {:db/ident :aws.v1.ec2.product.operating-system/rhel}
   {:db/ident :aws.v1.ec2.product.operating-system/suse}
   {:db/ident :aws.v1.ec2.product.operating-system/red-hat-ha}
   {:db/ident :aws.v1.ec2.product.operating-system/na}

   #:db{:ident       :aws.v1.ec2.product/license-model
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/usage-type
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/operation
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/capacity-status
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/dedicated-ebs-throughput
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/ecu
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/enhanced-networking-supported
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-sku
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/intel-avx-available
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/intel-avx2-available
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/intel-turbo-available
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/normalization-size-factor
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/pre-installed-sw
        :valueType   :db.type/ref
        :cardinality :db.cardinality/one}
   {:db/ident :aws.v1.ec2.product.pre-installed-sw/sql-ent}
   {:db/ident :aws.v1.ec2.product.pre-installed-sw/sql-web}
   {:db/ident :aws.v1.ec2.product.pre-installed-sw/sql-std}
   {:db/ident :aws.v1.ec2.product.pre-installed-sw/na}

   #:db{:ident       :aws.v1.ec2.product/processor-features
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   ;; optional
   #:db{:ident       :aws.v1.ec2.product/from-location-type
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-capacity-4xlarge
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-capacity-metal
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-capacity-24xlarge
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-capacity-10xlarge
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-capacity-9xlarge
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-capacity-large
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-capacity-32xlarge
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-capacity-18xlarge
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-capacity-2xlarge
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-capacity-12xlarge
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-capacity-16xlarge
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-capacity-8xlarge
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-capacity-xlarge
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance-capacity-medium
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/physical-cores
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/gpu-memory
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/group
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/max-volume-size
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/group-description
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/storage-media
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/to-location
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/max-iops-burst-performance
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/elastic-graphics-type
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/product-type
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/ebs-optimized
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/volume-type
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/instance
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/max-throughput-volume
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/to-location-type
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/transfer-type
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/max-iops-volume
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/gpu
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/resource-type
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/from-location
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/volume-api-name
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2.product/provisioned
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}

   #:db{:ident       :aws.v1.ec2/service-name
        :valueType   :db.type/string
        :cardinality :db.cardinality/one}])

(s/def :aws.v1.product/product-family
  (s/nilable
    #{"CPU Credits"
      "Compute Instance"
      "Compute Instance (bare metal)"
      "Data Transfer"
      "Dedicated Host"
      "EBS direct API Requests"
      "Elastic Graphics"
      "Fast Snapshot Restore"
      "Fee"
      "IP Address"
      "Load Balancer"
      "Load Balancer-Application"
      "Load Balancer-Network"
      "NAT Gateway"
      "Provisioned Throughput"
      "Storage"
      "Storage Snapshot"
      "EC2InstanceSavingsPlans"
      "ComputeSavingsPlans"
      "System Operation"}))

(defn product-entity-map
  [sku product]
  (let [{:strs [sku productFamily attributes]} product
        sku (h/conform! :aws.v1.product/sku sku {:product product})
        product-family (h/conform! :aws.v1.product/product-family productFamily {:product product})
        attributes-conformed (h/conform! :aws.v1.ec2.product/attributes attributes {:product product})]
    (-> (cond-> {:aws.v1.product/sku sku}
          ;; TODO: Remove nil product-family if AWS adds back in
          ;; On 2020-12-1 AWS removed product-family from certain products requiring us to make it nilable
          product-family (assoc :aws.v1.product/product-family product-family))
      (merge attributes-conformed)
      (assoc :aws.v1.ec2.product.usage-type/box-usage
        (boolean (re-find #"BoxUsage" (attributes-conformed :aws.v1.ec2.product/usage-type)))))))

(defn price-entity-map [term sku offer price]
  (let [{:strs [offerTermCode sku effectiveDate priceDimensions]} price
        sku (h/conform! :aws.v1.product/sku sku {:price price})
        offer-term-code (h/conform! :aws.v1.pricing/offer-term-code offerTermCode {:price price})
        effective-date (h/conform! :aws.v1.pricing/effective-date effectiveDate {:price price})
        price-dimensions (into [] (map #(h/conform! :aws.v1.ec2.pricing/price-dimensions % {:price price})) (vals priceDimensions))]
    (let [term-key (case term "OnDemand" :aws.v1.term/on-demand "Reserved" :aws.v1.term/reserved)]
      {:aws.v1.product/sku sku
       term-key            {:aws.v1.pricing/offer-term-code offer-term-code
                            :aws.v1.pricing/effective-date  effective-date
                            :aws.v1.pricing/dimensions      price-dimensions}})))

(defn -resource-price-per-hour*
  [db {:resource-price.aws-ec2/keys [instance-type region]}]
  (let [aws-region region
        [f :as price-list-items]
        (d/q '[:find ?v
               :in $ ?aws-region ?aws-instance-type
               :where
               [?e :aws.v1.ec2.product/location ?aws-region]
               [?e :aws.v1.ec2/instance-type ?aws-instance-type]
               ;; @NOTE : fixed-values taken from legacy resource_price.clj
               [?e :aws.v1.ec2.product.usage-type/box-usage true]
               [?e :aws.v1.ec2.product/operating-system :aws.v1.ec2.product.operating-system/linux]
               [?e :aws.v1.ec2.product/pre-installed-sw :aws.v1.ec2.product.pre-installed-sw/na]
               [?e :aws.v1.ec2.product/tenancy :aws.v1.ec2.product.tenancy/shared]

               [?e :aws.v1.term/on-demand ?t]
               [?t :aws.v1.pricing/dimensions ?d]
               [?d :aws.v1.pricing/unit :aws.v1.pricing.unit/hour]
               [?d :aws.v1.pricing.price-per-unit/value ?v]]
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

  (-resource-price-per-hour* db {:resource-price.aws-ec2/instance-type "m3.2xlarge"
                                 :resource-price.aws-ec2/region        "us-east-1"}))


