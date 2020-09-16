(ns org-crud.util
  (:require
   [clojure.set :as set]))

(defn ns-select-keys
  "Selects all keys from the provided `map` that match the given `ns-str`

  Pulled from `wing.core`.
  "
  [ns-str map]
  (into {} (filter (comp #{ns-str} namespace key)) map))

(defn ns-remove-keys
  [ns-str map]
  (into {} (remove (comp #{ns-str} namespace key)) map))

(comment
  (ns-remove-keys
    "org.prop"
    {:hi           :bye
     :org.prop/sup :whatever
     }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; get-all, get-one
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-all
  "Returns the maps that have the same key-values as in the passed
  pred map."
  [pred xs]
  (filter
    (fn [x]
      (apply
        (every-pred
          (fn [[k v]]
            (let [x-v (get x k)]
              (cond
                (and (fn? v)
                     ;; x-v must exist for most predicates
                     x-v)
                ;; if v is a function, treat it like a predicate
                (v x-v)

                ;; if v is a set, try set opts
                (set? v)
                (if (set? x-v)
                  (set/subset? v x-v)
                  (contains? v x-v))

                (set? x-v)
                (contains? x-v v)

                ;; otherwise, use v as comparison value
                :else (= v x-v)))))
        pred))
    xs))

(comment
  (every-pred [even?] [1 2 3])
  ((every-pred even?) 2 2 4)
  (set/subset? #{2} #{1 2}))

(defn get-one
  [pred xs]
  (let [res (get-all pred xs)]
    (when res
      (first res))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; url parsing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn re-get
  "Returns the first match for a passed regex and string.
  Shouldn't this function exist?
  "
  [pat s]
  (when s
    (let [parts (re-find pat s)]
      (when (> (count parts) 1)
        (second parts)))))

(def url-regex
  "https://stackoverflow.com/questions/3809401/what-is-a-good-regular-expression-to-match-a-url"
  #"(https?:[/][/](?:www\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\.[^\s]{2,}|www\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\.[^\s]{2,}|https?:[/][/](?:www\.|(?!www))[a-zA-Z0-9]+\.[^\s]{2,}|www\.[a-zA-Z0-9]+\.[^\s]{2,})")

(defn ->url [s]
  (re-get url-regex s))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; merge maps
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn merge-maps*
  "TODO could do some work to ensure order is maintained as expected"
  [a b]
  (merge-with
    (fn [a-v b-v]
      (let [vs (if (coll? a-v)
                 a-v
                 (list a-v))]
        (if (coll? b-v)
          (apply (partial conj vs) b-v)
          (conj vs b-v))))
    a b))

(defn merge-maps
  "Merges maps. If a matching key is found, those values are merged into a coll
  under that key.
  "
  [x & [y & maps]]
  (if-not y
    x
    (recur (merge-maps* x y) maps)))

(defn merge-maps-with-multi
  "Merges two maps. multi-keys is a set of keys that should be grouped with
  merge-maps. All other keys left to a typical merge operation.
  "
  [multi-keys & maps]
  (when maps
    (let [->map-multis  (fn [f m]
                          (into {}
                                (f (fn [[k _v]]
                                     (contains? multi-keys k))
                                   m)))
          multis-merged (apply merge-maps
                               (or (map (partial ->map-multis filter) maps) {}))
          merged        (apply merge
                               (map (partial ->map-multis remove) maps))]
      (merge merged multis-merged))))

(comment
  (merge-maps-with-multi #{})
  (->> [{:hello "world"} {:hello "sonny"}]
       (apply (partial merge-maps-with-multi #{})))
  (->> [{:hello "world"} {:hello "sonny"}]
       (apply (partial merge-maps-with-multi #{:hello}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; multi-group-by
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn multi-group-by
  [f xs]
  (reduce
    (fn [acc x]
      (->> (f x)
           (#(if (seq %) % [nil]))
           (reduce
             (fn [acc v]
               (update acc v conj x))
             acc)))
    {} xs))

(comment
  (multi-group-by
    :types
    [{:c :c :types [:a :b]}
     {:d :d :types []}]))
