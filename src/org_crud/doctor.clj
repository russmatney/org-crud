(ns org-crud.doctor
  (:require
   [babashka.fs :as fs]
   [org-crud.core :as core]
   [org-crud.update :as update]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; org helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn org-files
  "Fetches org-files.

  Only recurses one layer, collecting roughly todo/*, todo/garden/*."
  []
  (let [org-dir-path "/home/russ/todo"]
    (->> org-dir-path
         fs/list-dir
         (mapcat (fn [f]
                   (if (fs/directory? f)
                     (fs/list-dir f)
                     [f])))
         (remove fs/directory?)
         (filter (comp #{".org"} fs/extension)))))

(comment
  (count (org-files)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; duplicate ids
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn global-uuid-duplicates
  "
  Interactive mode is not quite ready from prime time.

  The update style currently drops src-blocks and items with logbooks.
  It also does not match tag spacing or property bucket order,
  which leads to annoying large file diffs

  Still useful for getting a list of the duplicated uuids.
  "
  ([fs] (global-uuid-duplicates {} fs))
  ([opts fs]
   (let [interactive? (:interactive? opts)
         items (->> fs (mapcat core/path->flattened-items))]
     (println "Checking for global uuids over " (count fs) " files and " (count items) " items.")
     (loop [remaining-items items
            checked-ids     #{}]
       (let [item (first remaining-items)
             id   (:org/id item)]
         (when (checked-ids id)
           (println "duplicate uuid found in file " (:org/source-file item) ".")
           ;; (println "item: " item)
           (println "id: " id)
           (println "name: " (:org/name item))
           (when interactive?
             (println "What to do? 'd'elete - delete UUID, 'D'ELETE - delete item, 'n'ew - gen new uuid for this item.")
             (let [input (read-line)]
               (case input
                 ;; NOTE some interactions might necessitate a re-parse of the file
                 ;; ex: if lines are deleted and line-nums are relied on for updating
                 "D" (println "saw D, do magic (not impled)")
                 "d" (println "saw d, do magic (not impled)")
                 "n" (do
                       (println "Updating items with this id in the file: "
                         (:org/source-file item))
                       ;; here we use a function, not the simpler update!, to avoid
                       ;; setting the same uuid to all the new found ones.
                       (update/update-path-with-fn!
                         (:org/source-file item)
                         (fn [it]
                           (when (= (:org/id it) id)
                             {:org/id (str (java.util.UUID/randomUUID))}))))))))
         (when (seq remaining-items)
           (recur (rest remaining-items) (conj checked-ids id))))))))

(comment
  (global-uuid-duplicates ["/home/russ/todo/prompts.org"])
  (global-uuid-duplicates
    {:interactive? true}
    ["/home/russ/todo/prompts.org"]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; cli interface
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn print-report [& _args]
  (println "Running org-crud doctor!")

  (let [file-paths (org-files)]
    (println "checking for global uuid duplicates")
    (global-uuid-duplicates file-paths)

    (println "Checking for broken org-links in your files")
    (println "TODO: impl broken link fixer")

    ))


(comment
  (print-report))
