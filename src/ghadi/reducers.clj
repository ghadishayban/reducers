(ns ghadi.reducers
  (:refer-clojure :exclude [range zipmap repeatedly iterate some count select-keys cycle repeat frequencies])
  (:require [clojure.core.protocols :as p])
  (:import java.util.Iterator
           ghadi.XFIterable))

(defmacro unless-reduced
  "A macro for correct reducible collections.
   Conditionally unwraps a Reduced or continues."
  [sym else]
  (assert (symbol? sym))
  `(if (reduced? ~sym)
     (deref ~sym)
     ~else))

(defn ^:private reducing-impl
  [this f init body]
  `(clojure.lang.IReduceInit
    (reduce [~this ~f ~init]
       ~@body)))

(defmacro reducible
  "Reifies CollReduce with the 1-arity signature
   delegating to the provided two argument signature"
  [bindings & body]
  (assert (and (vector? bindings)
               (= (clojure.core/count bindings) 2)
               (every? symbol? bindings)))
  (let [[f init] bindings]
    `(reify
       ~@(reducing-impl (gensym "this") f init body))))

(defn repeatedly
  "Like core/repeatedly but reducible and not seqable"
  [f]
  (reducible [rf init]
    (loop [acc init]
      (let [ret (rf acc (f))]
         (unless-reduced ret (recur ret))))))

(defn iterate
  "Like core/iterate but reducible and not seqable"
  [f seed]
  (reducible [rf init]
     (loop [ret (rf init seed) seed seed]
       (unless-reduced ret
          (let [next (f seed)]
            (recur (rf ret next) next))))))

(defn cycle
  "Like core/cycle but only reducible. Will coerce argument to vector"
  [coll]
  (let [coll (vec coll)
        n    (clojure.core/count coll)]
    (reducible [rf init]
      (if (pos? n)
        (loop [acc init i 0]
          (if (< i n)
            (let [ret (rf acc (coll i))
                  i   (unchecked-inc i)]
              (unless-reduced ret
                (recur ret (if (= i n) 0 i))))))
        init))))

;; alternatively (repeatedly (fn [] item))
(defn repeat
  [item]
  (reducible [rf init]
    (loop [ret init]
      (let [ret (rf ret item)]
        (unless-reduced ret (recur ret))))))

(def ^:private yield
  (fn
    ([result] result)
    ([result input]
       (when input
         (reduced input)))))

(defn yield-first
  "Reduces through a collection, yielding first truthy value."
  ([coll]
     (reduce yield nil coll))
  ([xfn coll]
     (transduce xfn yield nil coll)))

(defn some
  "Like core/some but uses reduce. Takes an optional transducer."
  ([pred coll]
     (yield-first (map pred) coll))
  ([pred xfn coll]
     (yield-first (comp xfn (map pred)) coll)))

;; locate / find-first / search
(defn any
  "Equivalent to (first (filter pred coll)) but uses reduce.
   Takes an optional transducer."
  ([pred coll]
     (yield-first (filter pred) coll))
  ([pred xfn coll]
     (yield-first (comp xfn (filter pred)) coll)))

(def ^:private counting
  (fn
    ([n] n)
    ([n _] (inc n))))

(defn count
  "Like core/count but for reducible collections.
   Takes optional transducer"
  ([coll]
     (reduce counting 0 coll))
  ([xfn coll]
     (transduce xfn counting 0 coll)))

;;
;; range
;;

(deftype RangeIterator [^long ^:unsynchronized-mutable i
                        ^long end
                        ^long step]
  Iterator
  (hasNext [_]
    (if (pos? step)
      (< i end)
      (> i end)))
  (next [_]
    (let [ret i]
      (set! i (+ i step))
      ret)))

(defmacro ^:private range-loop*
  [comp]
  `(loop ~'[acc init i start]
     (if (~comp ~'i ~'end)
       (let ~'[ret (f acc i)]
         (unless-reduced ~'ret ~'(recur ret (+ i step))))
      ~'acc)))

(deftype Range [^Long start ^Long end ^Long step]
  Iterable
  (iterator [_]
    (RangeIterator. start end step))
  clojure.lang.IReduceInit
  (reduce [_ f init]
    (if (pos? step)
      (range-loop* <)
      (range-loop* >))))

(defn range
  "Unlike core/range accepts only Longs. Step must be non-zero.
   Natively Iterable and reducible, but not seqable."
  ([] (range 0 Long/MAX_VALUE 1))
  ([end] (range 0 end 1))
  ([start end] (range start end 1))
  ([start end step]
     (if (or (not (integer? step))
             (zero? step))
       (throw (IllegalArgumentException. "step must be non-zero integer, consider using repeatedly.")))
     (Range. start end step)))

(defn zipmap
  "Unlike core's zipmap, uses iterators and transients"
  [keys vals]
  (let [keys (clojure.lang.RT/iter keys)
        vals (clojure.lang.RT/iter vals)]
    (loop [m (transient {})]
      (if (and (.hasNext keys) (.hasNext vals))
        (recur (assoc! m (.next keys) (.next vals)))
        (persistent! m)))))

(defn select-keys
  "Like core/select-keys but transduces over transients"
  [map keyseq]
  (into {} (keep #(find map %)) keyseq))

(defn transiterate
  "Takes an Iterable and a transducer and returns a transformed Iterable.
   Analogous to (sequence xfn coll) but for Iterables"
  [xfn iterable]
  (XFIterable. xfn iterable))

(defn frequencies
  "Like core/frequencies but takes a transducer"
  [xfn coll]
  (let [rf (fn
             ([counts]
                (persistent! counts))
             ([counts x]
                (assoc! counts x (inc (get counts x 0)))))]
    (transduce xfn rf (transient {}) coll)))
