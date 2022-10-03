(ns computesoftware.csp-pricing.impl.json-reader
  "Helpers to read (a subset of) JSON in a streaming way."
  (:import [com.fasterxml.jackson.core JsonFactory JsonParser JsonToken]))

(defn- ^JsonParser parser
  "Create and initialize a parser for the specified input (which can be a string,
   a reader, an inputstream, a file, a url, arrays etc.)"
  [in]
  (doto (.createParser (JsonFactory.) in) .nextToken))

(defn- read-array
  "Read an array, calling (read-item p acc) for each item; the return value of
   read-item becomes the acc passed to the next call to read-item.
   Returns the last value of acc."
  [^JsonParser p acc read-item]
  (let [t (.currentToken p)]
    (when-not (identical? JsonToken/START_ARRAY t)
      (throw (ex-info "Unexpected JSON token"
               {:token t :offset (.getTextOffset p)})))
    (.nextToken p)
    (loop [acc acc]
      (let [t (.currentToken p)]
        (if (identical? JsonToken/END_ARRAY t)
          (do (.nextToken p) acc)
          (recur (read-item p acc)))))))

(defn- read-object
  "Read an object, calling first (readers-by-key field-name) to retrieve a reader function f.
   (When f is nil an exception is thrown as it means the field is unexpected.)
   Then (f p acc) is called to read the value, its return value becoming the value of acc
   for the next key-value.
   Returns the last value of acc."
  [^JsonParser p acc readers-by-key]
  (let [t (.currentToken p)]
    (when-not (identical? JsonToken/START_OBJECT t)
      (throw (ex-info "Unexpected JSON token"
               {:token t :offset (.getTextOffset p)})))
    (.nextToken p)
    (loop [acc acc]
      (let [t (.currentToken p)]
        (if (identical? JsonToken/END_OBJECT t)
          (do (.nextToken p) acc)
          (if-some [reader (readers-by-key (.getText p))]
            (recur (reader (doto p .nextToken) acc))
            (throw (ex-info "Unexpected JSON field"
                     {:field (.getText p) :offset (.getTextOffset p)}))))))))

(defn- read-value
  "Eagerly read a JSON value (as an EDN value) from p."
  [^JsonParser p]
  (let [t (.currentToken p)]
    (condp identical? t
      JsonToken/START_ARRAY
      (persistent! (read-array p (transient [])
                     (fn [p v] (conj! v (read-value p)))))
      JsonToken/START_OBJECT
      (persistent! (read-object p (transient {})
                     (constantly (fn [p m] (assoc! m (.getCurrentName p) (read-value p))))))
      JsonToken/VALUE_STRING (let [s (.getText p)] (.nextToken p) s)
      JsonToken/VALUE_NUMBER_INT (let [n (.getLongValue p)] (.nextToken p) n)
      JsonToken/VALUE_NUMBER_FLOAT (let [n (.getDoubleValue p)] (.nextToken p) n)
      JsonToken/VALUE_FALSE (do (.nextToken p) false)
      JsonToken/VALUE_TRUE (do (.nextToken p) true)
      JsonToken/VALUE_NULL (do (.nextToken p) nil)
      (throw (ex-info "Unexpected JSON token"
               {:token t :offset (.getTextOffset p) :p p})))))

(defn read-gcp-pricing
  "Reads GCP pricing from input.
   For each product (read-sku sku-acc sku-map) is called.
   Returns {:skus sku-acc :next-page-token token}"
  [in sku-acc read-sku]
  (with-open [p (parser in)]
    (read-object p {}
      {"skus"          (fn [p acc]
                         (assoc acc :skus
                           (read-array p sku-acc
                             (fn [p sku-acc]
                               (read-sku sku-acc (read-value p))))))
       "nextPageToken" (fn [p acc]
                         (assoc acc :next-page-token
                           (read-value p)))})))

(defn read-gcp-services
  "Reads GCP services from input.
   For each product (rf acc service-map) is called.
   If token found (page acc token) is called.
   Returns acc."
  [in rf acc page]
  (with-open [p (parser in)]
    (read-object p {}
      {"services"      (fn [p acc]
                         (read-array p acc
                           (fn [p acc]
                             (rf acc (read-value p)))))
       "nextPageToken" (fn [p acc]
                         (let [token (read-value p)]
                           (cond-> acc
                             (not= "" token) (page token))))})))

(defn- object-reader [readers-by-key]
  (fn [p acc] (read-object p acc readers-by-key)))

(defn- array-reader [item-reader]
  (fn [p acc] (read-array p acc item-reader)))

(defn- value-reader [f]
  (fn [p acc] (f acc (read-value p))))

(def ^:private discard-reader
  (value-reader (fn [acc _] acc)))

(defn scan-gcp-token
  "Scans a gcp response for the top-level nextPageToken value and returns it (may be the empty string)."
  [in]
  (with-open [p (parser in)]
    (read-object p nil
      (fn [top-level-field]
        (case top-level-field
          "nextPageToken" (fn [p acc] (read-value p))
          discard-reader)))))

(defn read-aws-pricing
  "Reads AWS pricing from input.
   read-product takes the accumulator, a sku and a product and returns an updated accumulator,
   read-price takes the accumulator, a term string (\"OnDemand\" or \"Reserved\"), the product sku, the offer (an id) and the price map
   and returns the updated accumulator.
   Returns {:meta file-metadata :summary acc}"
  [in acc read-product read-price]
  (with-open [p (parser in)]
    (read-object p {:summary acc :meta {}}
      (fn [field-name]
        (case field-name
          "products"
          (fn [p acc]
            (assoc acc
              :summary
              (read-object p (:summary acc)
                (fn [sku]
                  (value-reader
                    (fn [acc product-map]
                      (read-product acc sku product-map)))))))
          "terms"
          (fn [p acc]
            (assoc acc
              :summary
              (read-object p (:summary acc)
                (fn [term]
                  (object-reader
                    (fn [sku]
                      (object-reader
                        (fn [offer]
                          (value-reader
                            (fn [acc price-map]
                              (read-price acc term sku offer price-map)))))))))))
          ; else
          (value-reader (fn [acc v] (assoc-in acc [:meta field-name] v))))))))

(defn read-aws-savings-plan-index
  "Returns a collection of paths."
  [in]
  (with-open [p (parser in)]
    (read-object
      p []
      (fn [field-name]
        (case field-name
          "regions" (array-reader
                      (object-reader
                        (fn [field-name]
                          (case field-name
                            "versionUrl" (value-reader (fn [acc url] (conj acc url)))
                            discard-reader))))
          discard-reader)))))

(defn read-aws-savings-plan-pricing
  "Reads AWS savings plan pricing from input.
   read-product takes the accumulator, a product (a saving plan) and returns an updated accumulator,
   read-prices takes the accumulator, a string (\"savingsPlan\") and the prices map (under which discounted prices for all EC2/Lambda/ECS skus are found)
   of a saving plan and returns an updated accumulator.
   Returns {:meta file-metadata :summary acc}"
  [in acc read-product read-prices]
  (with-open [p (parser in)]
    (read-object p {:summary acc :meta {}}
      (fn [field-name]
        (case field-name
          "products"
          (fn [p acc]
            (assoc acc
              :summary
              (read-array p (:summary acc)
                (value-reader
                  (fn [acc product-map]
                    (read-product acc product-map))))))
          "terms"                                           ; “terms” -> “savingsPlan” -> [] -> (“sku” “description” “effectiveDate” “leaseContractLength” “rates”)
          (fn [p acc]
            (assoc acc
              :summary
              (read-object p (:summary acc)
                (fn [term]                                  ; must be savingsPlan
                  (array-reader
                    (value-reader
                      (fn [acc prices-map]
                        (read-prices acc term prices-map))))))))
          (value-reader (fn [acc v] (assoc-in acc [:meta field-name] v))))))))

(defn read-azure-services
  "Reads Azure services from input.
   For each meter (rf acc meter-map) is called.
   Returns {\"Meters\" acc ... other fields}."
  [in rf acc]
  (with-open [p (parser in)]
    (read-object p {"Meters" acc}
      (fn [field-name]
        (case field-name
          "Meters"
          (fn [p acc]
            (assoc acc "Meters"
              (read-array p (get acc "Meters")
                (fn [p acc]
                  (rf acc (read-value p))))))
          (value-reader (fn [acc v] (assoc acc field-name v))))))))

(defn read-any [in]
  (with-open [p (parser in)]
    (read-value p)))
