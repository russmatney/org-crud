(ns org-crud.cli
  (:require [org-crud.markdown :as markdown]))

(defn org-to-markdown [& args]
  (if-not (= (count args) 2)
    (println "Expected call format: `org-to-markdown <src-dir> <out-dir>`")
    (let [src-dir (first args)
          out-dir (second args)]
      (markdown/org-dir->md-dir src-dir out-dir))))

(def commands {"org-to-markdown" org-to-markdown})

(defn print-expectations []
  (println "Expected one of:" (keys commands)))

(defn -main [& args]
  (if-not (seq args)
    (do
      (println "No args passed")
      (print-expectations))
    (let [cmd  (first args)
          cmd  (commands cmd)
          args (rest args)]
      (if-not cmd
        (do
          (println "No command found for args: " args)
          (print-expectations))
        (apply cmd args)))))
