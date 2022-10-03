(ns computesoftware.csp-pricing
  (:require
    [computesoftware.csp-pricing.qbe :as qbe]
    [datomic.client.api :as d]
    [kwill.anomkit :as ak]))

(defn pricing-db
  "Retrieves current stable pricing db."
  [conn]
  (if-some [tx-id (ffirst (d/q '[:find ?t
                                 :where
                                 [?e :db/ident :cs.pricing-api/stable-snapshot] ; explicit lookup to avoid exception
                                 [?e :cs.pricing-api/tx ?t]] (d/db conn)))]
    (d/as-of (d/db conn) tx-id)
    (d/db conn)))

(defn qbe-expand
  "Returns the actual Datomic query the qbe query expands to."
  [arg-map]
  (qbe/to-datomic-query (:q arg-map)))

(defn qbe
  [{:keys [qbe args]}]
  (let [qmap (try
               (qbe/datomic-qmap {:query qbe :args args})
               (catch Throwable t (ak/?! (ak/ex->anomaly t))))]
    (d/q qmap)))

(comment
  (qbe {:qbe #:aws.v1.workspaces.product
                     {:aws.v1.term/on-demand
                      {:aws.v1.pricing/dimensions
                       {:aws.v1.pricing.price-per-unit/value '?base-price}}
                      :attributes       :aws.v1.workspaces.product.attributes/hardware
                      :location         "us-east-1"
                      :operating-system :aws.v1.workspaces.product.operating-system/windows
                      :bundle           "Standard"
                      :running-mode     '?aws-running-mode
                      :group            '?aws-group
                      :root-volume      '?root-volume
                      :user-volume      '?user-volume}}))
