(ns org-crud.core
  (:require
   [organum.core :as org]
   [me.raynes.fs :as fs]
   [org-crud.headline :as headline]
   [clojure.walk :as walk]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parse helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; DEPRECATED
(defn parse-headlines
  "Return just the headlines from an org file as strings."
  [{:keys [parsed]}]
  (let [sections (filter #(= (:type %) :section) parsed)]
    (map :name sections)))

(defn parse-org-file
  [path]
  (let [parsed (org/parse-file (fs/absolute path))]
    {:filename (fs/base-name path)
     :parsed   parsed}))

(defn parse-org-lines
  "Very close to the internal org/parse-file function,
  except that it expects a seq of text lines."
  [lines]
  (when (seq lines)
    (reduce org/handle-line [(org/root)] lines)))

(comment
  (parse-org-file "repos.org"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing basic items for all headlines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parsed->items
  "Only parses :type :section (skipping :root)"
  [parsed]
  (reduce
    (fn [items next]
      (conj items (merge {:org-section next}
                         (headline/->item next))))
    []
    parsed))

(defn path->items
  [p]
  (->> p
       parse-org-file
       :parsed
       parsed->items
       (remove (comp nil? :name))))

(defn flattened->nested
  "Returns top-level items with sub-items as children"
  ([xs]
   (flattened->nested
     (fn [parent item]
       (update parent :items conj item)) xs))
  ([update-parent xs]
   (loop [remaining  xs
          items      []
          ctx-stack  []
          last-level 0]
     (let [{:keys [level] :as current} (some-> remaining first)
           level                       (if (= :root level) 0 level)
           level-diff                  (when current (- level last-level))]

       (if (and current (> level-diff 0))
         ;; recur with updated
         (recur (rest remaining)
                items
                ;; push current to ctx-stack
                (conj ctx-stack current)
                level)

         ;; pop as many times as diff in level
         (let [{:keys [ctx-stack items]}
               (reduce
                 (fn [{:keys [ctx-stack items]} _]
                   ;; merge top of stack with item below it
                   (let [top-of-stack (peek ctx-stack)
                         ctx-stack    (when top-of-stack (pop ctx-stack))
                         parent       (when (seq ctx-stack)
                                        (some-> ctx-stack peek
                                                (update-parent top-of-stack)))
                         ctx-stack    (when parent
                                        ;; replace top of stack with updated
                                        ;; parent
                                        (-> ctx-stack pop (conj parent)))
                         ;; if we have a parent, don't touch items
                         ;; otherwise, add this to items
                         items        (if parent items
                                          (conj items top-of-stack))]
                     {:ctx-stack ctx-stack
                      :items     items}))
                 {:ctx-stack ctx-stack
                  :items     items}
                 (if current
                   (range level-diff 1)
                   ctx-stack))]
           ;; recur with updated
           (if current
             (recur (rest remaining) items (conj ctx-stack current) level)
             items)))))))

(comment
  (range -1 1)
  (->
    (flattened->nested
      (fn [parent item]
        (update parent :items conj
                (-> item (update :items reverse))))
      [{:level 1 :name "b"}
       {:level 1 :name "a"}
       {:level 2 :name "c"}
       {:level 3 :name "d"}
       {:level 4 :name "e"}
       {:level 2 :name "f"}
       {:level 1 :name "g"}])
    println)

  (walk/postwalk (fn [x]
                   (if (and (map? x)
                            (seq (x :items)))
                     (update x :items reverse)
                     x))
                 [{:level 1 :name "a"}
                  {:level 1 :name "b" :items
                   [{:level 2 :name "f"}
                    {:level 2 :name "c"
                     :items [{:level 3 :name "d"
                              :items [{:level 4 :name "e"}]}]}]}
                  {:level 1 :name "g"}]))

(defn parsed->items-with-nested
  "Parses level-1 items with sub-sections as children"
  [parsed]
  (->> parsed
       ;; (filter #(= :section (:type %)))
       (map headline/->item)
       (flattened->nested
         (fn [parent item] (update parent :items conj item)))
       (walk/postwalk (fn [x]
                        (if (and (map? x)
                                 (seq (:items x)))
                          (update x :items reverse)
                          x)))))

(defn path->items-with-nested
  [p]
  (->> p
       parse-org-file
       :parsed
       parsed->items-with-nested
       (remove nil?)
       (map #(assoc % :source-file p))
       ))

(defn dir->items
  [dir]
  (->> (fs/list-dir dir)
       (filter #(contains? #{".org"} (fs/extension %)))
       (mapcat path->items-with-nested)))

(comment
  (-> (dir->items "/home/russ/Dropbox/roam")
      first))
