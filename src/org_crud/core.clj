(ns org-crud.core
  (:require
   [organum.core :as org]
   [clojure.walk :as walk]
   [clojure.string :as string]
   [babashka.fs :as fs]
   [org-crud.node :as node]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parse helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-org-file
  [path]
  (try
    (-> path
        fs/absolutize
        str
        org/parse-file)
    (catch Exception ex
      (println "org-crud.core/parse-org-file exception" path)
      (println ex)
      nil)))

(comment
  (parse-org-file "repos.org"))

(defn parse-org-lines
  "Very close to the internal org/parse-file function,
  except that it expects a seq of text lines.

  Used to get basic text into the item's 'body' structure
  for commit messages and other non-org sources.
  "
  [lines]
  (when (seq lines)
    (reduce #'org/handle-line [(#'org/root)] lines)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing flattened items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parsed->flattened-items
  "Only parses :type :section (skipping :root).

  Produces flattened items, rather than nested.
  This means deeper org nodes will not be contained within parents.
  "
  [source-file parsed]
  (when (and parsed source-file)
    (reduce
      (fn [items next]
        (conj items (merge
                      ;; {:org-section next}
                      (node/->item next source-file))))
      []
      parsed)))

(defn path->flattened-items
  "Returns a flattened list of org items in the passed file.

  Produces flattened items, rather than nested.
  This means deeper org nodes will not be contained within parents.
  See `path->nested-items`."
  [p]
  (->> p
       parse-org-file
       (parsed->flattened-items p)
       (remove nil?)
       (remove (comp nil? :org/name))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing nested items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn flattened->nested
  "Returns top-level items with sub-items as children"
  ([xs]
   (flattened->nested
     (fn [parent item]
       (update parent :org/items conj item)) xs))
  ([update-parent xs]
   (loop [remaining  xs
          items      []
          ctx-stack  []
          last-level 0]
     (let [{:keys [org/level] :as current}
           (some-> remaining first)
           level      (if (= :level/root level) 0 level)
           level-diff (when current (- level last-level))]

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
  (->
    (flattened->nested
      (fn [parent item]
        (update parent :org/items conj
                (-> item (update :org/items reverse))))
      [{:org/level 1 :org/name "b"}
       {:org/level 1 :org/name "a"}
       {:org/level 2 :org/name "c"}
       {:org/level 3 :org/name "d"}
       {:org/level 4 :org/name "e"}
       {:org/level 2 :org/name "f"}
       {:org/level 1 :org/name "g"}])
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

(defn parsed->nested-items
  "Parses level-1 items with sub-sections as children"
  [source-file parsed]
  (when (and source-file parsed)
    (->> parsed
         ;; (filter #(= :section (:type %)))
         (map (fn [raw] (node/->item raw source-file)))
         (flattened->nested
           (fn [parent item] (update parent :org/items conj item)))
         (walk/postwalk (fn [x]
                          (if (and (map? x)
                                   (seq (:org/items x)))
                            (update x :org/items reverse)
                            x))))))

(defn path->nested-item
  "Produces a single item, the :root of the org file.
  Level-1 sections can be found as :items, all children are nested as
  further lists of :items all the way down.

  If you want the org file as a flattened list, see `path->flattened-items`.
  "
  [p]
  (some->> p
           parse-org-file
           (parsed->nested-items p)
           (remove nil?)
           first))

(defn dir->nested-items
  "For now, :recursive? goes one layer down.

  TODO impl with loop to recur properly."
  ([dir] (dir->nested-items {} dir))
  ([opts dir]
   (->> dir
        fs/list-dir
        (map (fn [f]
               (if (and (:recursive? opts)
                        (not (string/includes? f ".git"))
                        (fs/directory? f))
                 (fs/list-dir f)
                 [f])))
        (apply concat)
        (filter #(contains? #{".org"} (fs/extension %)))
        (map path->nested-item)
        (remove nil?))))

(comment
  (-> (dir->nested-items "/home/russ/Dropbox/notes")
      count)
  (-> (dir->nested-items {:recursive? true} "/home/russ/Dropbox/notes")
      count)

  (-> (dir->nested-items "/home/russ/Dropbox/notes")
      first))
