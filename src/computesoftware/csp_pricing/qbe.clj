(ns computesoftware.csp-pricing.qbe
  "Query by example"
  (:require
    [datomic.client.api :as d]
    [datomic.client.api.async :as da]))

(def ^:private ^:dynamic *selectivities*
  "map of attrs to selectivity (double)"
  {})

(defn- selectivity [k]
  (- (*selectivities* k 1.0)))

(defn- intersection
  ([a] a)
  ([a b] (into #{} (filter a) b)))

(defn- free-vars [pat]
  (cond
    (= '_ pat) nil
    (symbol? pat) [pat]
    (map? pat) (mapcat free-vars (vals pat))
    (set? pat)
    (when-some [[pat & pats] (seq pat)]
      (transduce (map free-vars)
        intersection (set (free-vars pat)) pats))
    (vector? pat) (mapcat free-vars pat)))

(comment
  (s/def ::entity
    (s/map-of qualified-keyword? ::pattern))

  (s/def ::pattern
    (s/or
      :many (s/coll-of ::pattern :kind vector?)
      :one-of (s/keys :req-un [::one-of])
      :entity ::entity
      :wildcard #{'_}
      :var simple-keyword?
      :value ::value))

  (s/def ::one-of (s/coll-of ::value))

  (s/def ::value (some-fn string? boolean? keyword? number?
                   inst? uuid?)))

(defn- map-to-preds
  ([m] (map-to-preds (or (:db/id m) (gensym '?root)) (dissoc m :db/id)))
  ([var m]
   (->>
     (sort-by (comp selectivity key) m)
     (mapcat
       (fn entry-to-pats [[attr pat]]
         (cond
           (vector? pat)
           (mapcat (fn [item-pat]
                     (entry-to-pats [attr item-pat]))
             pat)
           (seq? pat) (throw (ex-info "Lists as patterns are reserved for future use." {:pat pat}))
           (set? pat) (throw (ex-info "Sets as patterns are reserved for future use." {:pat pat}))
           #_(if (seq pat)
               (let [vars (cons var (free-vars pat))]
                 [(list* 'or-join vars
                    (for [pat pat]
                      (cons 'and (entry-to-pats [attr pat]))))])
               ; empty set is only satisfied by inexistence
               [['not [var attr]]])
           (map? pat)
           (if (:one-of pat)
             (let [var' (or (:as pat) (gensym (str "?" (name attr))))]
               [(into ['or-join [var var']]
                  (for [option (:one-of pat)]
                    ['and [var attr option] [['identity option] var']]))])
             (let [var' (or (:db/id pat) (gensym (str "?" (name attr))))
                   pat (dissoc pat :db/id)]
               (cons
                 [var attr var']
                 (map-to-preds var' pat))))
           (= '_ pat) [[var attr pat]]
           (symbol? pat) (let [tmp (gensym (str pat "-raw"))]
                           [[var attr tmp] ['maybe-resolve-ident attr tmp pat]])
           :else [[var attr pat]]))))))

(defn- pred-free-vars [pred]
  (cond
    (map? pred)
    (mapcat
      (fn [[attr pat]]
        (cond
          (coll? pat) (free-vars pat)
          (= '_ pat) nil
          (symbol? pat) [pat]))
      pred)
    (vector? pred)
    (filter symbol? pred)))

(defn to-datomic-query
  ([query] (to-datomic-query query {}))
  ([query selectivities]
   (binding [*selectivities* (merge *selectivities* selectivities)]
     (let [query (cond-> query (map? query) vector)
           [_ parts] (reduce (fn [[current parts] x]
                               (if (keyword? x)
                                 (case x (:find :in :where) [x parts])
                                 [current (update parts current (fnil conj []) x)]))
                       [:where {}] query)
           preds (:where parts)
           inputs (cons '% (:in parts '[$]))
           find (if-some [find-spec (:find parts)]
                  (cons ':find find-spec)
                  (let [vars (into #{} (mapcat pred-free-vars) preds)
                        vars (reduce disj vars inputs)]
                    `(:find ~@vars :keys ~@(map #(-> % name (subs 1) symbol) vars))))
           where (cons ':where
                   (mapcat
                     (fn [pred]
                       (if (map? pred)
                         (map-to-preds pred)
                         [pred]))
                     preds))]
       (vec (concat find (cons :in inputs) where))))))

(def ^:private rules
  '[[(maybe-resolve-ident [[?a ?v] ?id])
     [?a :db/valueType :db.type/ref]
     [(get-else $ ?v :db/ident ?v) ?id]]
    [(maybe-resolve-ident [[?a ?v] ?v'])
     [?a :db/valueType ?type]
     [?ref :db/ident :db.type/ref]
     [(!= ?ref ?type)]
     [(identity ?v) ?v']]])

(defn datomic-qmap
  ([qbe-qmap]
   (-> qbe-qmap
     (dissoc :selectivities)
     (update :query to-datomic-query (:selectivities qbe-qmap))
     (update :args #(cons rules %))))
  ([qbe-q & args]
   (datomic-qmap {:query qbe-q :args args})))

(defn aq
  "Takes either a single argument (a map with :query (QBE), :arg and :selectivities (optional) entries)
   or a QBE query follwed by arguments.
   Returns matches."
  [q & args]
  (da/q (apply datomic-qmap q args)))

(defn q
  "Takes either a single argument (a map with :query (QBE), :arg and :selectivities (optional) entries)
   or a QBE query follwed by arguments.
   Returns matches."
  [q & args]
  (d/q (apply datomic-qmap q args)))

(comment
  (to-datomic-query
    [#:gc.v1.compute.product
            {:service-regions "us-west3"
             :category
             #:gc.v1.compute.product.category
                     {:resource-group "SSD"
                      :usage-type     "OnDemand"
                      :price          '?price}}])

  (qbe/q
    #:aws.v1.workspaces.product
            {:aws.v1.term/on-demand
             {:aws.v1.pricing/dimensions
              {:aws.v1.pricing.price-per-unit/value '?base-price}}
             :attributes       :aws.v1.workspaces.product.attributes/hardware
             :location         "US East (N. Virginia)"
             :operating-system :aws.v1.workspaces.product.operating-system/windows
             :bundle           {:as     '?bundle
                                :one-of #{"Standard" "Standard Plus"}}
             :running-mode     '?aws-running-mode
             :group            '?aws-group
             :root-volume      '?root-volume
             :user-volume      '?user-volume}
    (d/db conn))

  (qbe/q
    [:find '?base-price '?bundle
     :in '$ '[?bundle ...]
     :where
     #:aws.v1.workspaces.product
             {:aws.v1.term/on-demand
              {:aws.v1.pricing/dimensions
               {:aws.v1.pricing.price-per-unit/value '?base-price}}
              :attributes       :aws.v1.workspaces.product.attributes/hardware
              :location         "US East (N. Virginia)"
              :operating-system :aws.v1.workspaces.product.operating-system/windows
              :bundle           '?bundle
              :running-mode     '?aws-running-mode
              :group            '?aws-group
              :root-volume      '?root-volume
              :user-volume      '?user-volume}]
    db ["Standard" "Performance"])

  ; expands to

  [:find ?price
   :keys price
   :in $
   :where
   [?root12149 :gc.v1.compute.product/service-regions "us-west3"]
   [?root12149 :gc.v1.compute.product/category ?category12150]
   [?category12150 :gc.v1.compute.product.category/resource-group "SSD"]
   [?category12150 :gc.v1.compute.product.category/usage-type "OnDemand"]
   [?category12150 :gc.v1.compute.product.category/price ?price]])
