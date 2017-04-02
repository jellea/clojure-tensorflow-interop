(ns clojure-tensorflow-interop.utils
  (:import [org.tensorflow
            Tensor
            Shape
            DataType
            ]))


(defn recursively
  "Apply function to all items in nested data structure if
  condition function is met."
  [apply-if-fn func data]
  (if (apply-if-fn data)
    (func (map (partial recursively apply-if-fn func) data))
    data))


(defn make-coll
  "Make a collection of x,y,z... dimensions"
  [fill & dims]
  (case (count dims)
    0 fill
    1 (repeat (first dims) fill)
    (repeat (first dims) (apply make-coll (rest dims)))
    ))

(def array?
  "Works like coll? but returns true if argument is array"
  #(= \[ (first (.getName (.getClass %)))))

(defn tf-vals [v]
  "Convert value into type acceptable to TensorFlow
  Persistent data structures become arrays
  Longs become 32bit integers
  Doubles become floats"
  (cond
    (coll? v)
    (if (coll? (first v))
      (to-array (map tf-vals v))
      (case (.getName (type (first v)))
        "java.lang.Long" (int-array v)
        "java.lang.Double" (float-array v)))
    (= (.getName (type v)) "java.lang.Long") (int v)
    (= (.getName (type v)) "java.lang.Double") (float v)
    ;; anything else
    true v))

(def tensor->clj (comp (partial recursively array? vec) get-tensor-val))

(def clj->tensor #(Tensor/create (tf-vals %)))

(def tensor->shape
  #(let [arr (.shape %)]
     (if (> (count arr) 0)
     (Shape/make
      (aget arr 0)
      (java.util.Arrays/copyOfRange arr 1 (count arr)))
     (Shape/scalar)
     ))
  )

(count (int-array [1 0]))

(defn output-shape [tensor]
  (let [shape (tensor->shape tensor)
        dims (map #(.size shape %)
                  (range (.numDimensions shape)))
        d (case (.name (.dataType tensor))
                "INT32" 0
                "FLOAT" 0.0)]
    (tf-vals
     (apply make-coll d dims))))

(defn get-tensor-val [tensor]
  (let [copy-to (output-shape tensor)]
    (cond
      (array? copy-to) (.copyTo tensor copy-to)
      (float? copy-to) (.floatValue tensor)
      ((complement float?) copy-to) (.intValue tensor))))


(defn thread
  "Approximately equivalent to -> macro.
  Required because -> must run at compile time"
  [val functions] (reduce #(%2 %1) val functions))
