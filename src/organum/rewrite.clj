(ns organum.rewrite
  "From: https://github.com/seylerius/organum"
  (:require [instaparse.core :as insta]
            [babashka.fs :as fs]))

;; Parsers

(def doc-metadata
  (insta/parser
    "<document> = token (ows token)*
    <token> = metadata / content
    <metadata> = title | author | date
    title = <'#+title: '> #'.*'
    author = <'#+author: '> #'.*'
    <ows> = <#'[\\s\r\n]*'>
    date = <'#+date: '> #'.*'
    <content> = #'(?s).*'"))

(def headlines
  (insta/parser
    "<S> = token (<brs> token)*
    <token> = section / content
    section = br? h ws? (brs content)*
    h = stars ws headline
    <headline> = keyed / unkeyed
    <keyed> = keyword ws unkeyed
    <unkeyed> = prioritized / title
    <prioritized> = priority ws title
    <title> = (#'.'+ ws* tags) / #'.+'
    stars = #'\\*+'
    keyword = #'TODO|DONE'
    priority = <'[#'> #'[a-zA-Z]' <']'>
    tags = <':'> (tag <':'>)+ ws?
    <tag> = #'[a-zA-Z0-9_@]+'
    <ws> = <#'[^\\S\\r\\n]+'>
    <brs> = (br br br br+) / (br br br+) / (br br+) / br+
    br = <#'\\r?\\n'>
    <content> = #'^([^*].*)?'"))

#_(def inline-markup
    (insta/parser
      "<inline> = (b | i | u | strike | verbatim | code | super | sub | string)+
    b = <'*'> inline <'*'>
    i = <'/'> inline <'/'>
    u = <'_'> inline <'_'>
    strike = <'+'> inline <'+'>
    verbatim = <'='> '[^=]+' <'='>
    code = <'~'> #'[^~]+' <'~'>
    super = <'^'> (#'\\w' | <'{'> inline <'}'>)
    sub = <'_'> (#'\\w' | <'{'> inline <'}'>)
    <string> = '\\\\*' | '\\\\/' | '\\\\_' | '\\\\+' | '\\\\='  '\\\\~' | '\\\\^' | #'[^*/_+=~^_\\\\]*'"))

;; (def is-table
;;   (insta/parser
;;     "<S> = (table-row / content)+
;;    table-row = #'^\\|[^\\r\\n]+' <br>?
;;    br = #'\\r\\n' / #'[\\r\\n]'
;;    <brs> = (br br br br+) / (br br br+) / (br br+) / br+
;;    <content> = #'^[^|\\n\\r][^\\n\\r]*' brs?"))

;; (def org-tables
;;   (insta/parser
;;     "table = th? tr+
;;    th = tr-start td+ line-break horiz-line
;;    <line-break> = <#'[\\r\\n]'>
;;    <horiz-line> = <#'[-|+]+'>
;;    tr = tr-start td+
;;    <tr-start> = <'|'> <#'[\\s]+'>
;;    td = contents td-end
;;    <td-end> = <#'\\s+|'>
;;    <contents> = #'(\\s*[^|\\s]+)+'"))

;; Fixers

(defn tree-fixer
  [tree item]
  (cond (vector? item)
        (conj tree (vec (reduce tree-fixer [] item)))

        (and (coll? item) (not (vector? item)))
        (apply concat tree (map (partial tree-fixer []) item))

        :else (conj tree item)))

(defn fix-tree
  [tree]
  (reduce tree-fixer '() tree))

(defn clean-headline
  [stars & args]
  (let [level       (keyword (str "h" (count (second stars))))
        first-lists (->> args
                         (take-while (complement string?))
                         (drop-while string?))
        title       (->> args
                         (drop-while (complement string?))
                         (take-while string?)
                         (apply str)
                         ;; inline-markup
                         )
        last-lists  (->> args
                         (drop-while (complement string?))
                         (drop-while string?))]
    (vec (concat [level] first-lists [title] last-lists))))

#_(defn rejoin-lines
    "Rejoin lines with appropriate line breaks."
    [coll]
    (reduce #(if (string? (first %2))
               (conj %1 (apply str %2))
               (apply conj %1 %2))
            [] (partition-by string? (replace {[:br] "\n"} coll))))

;; Filters

(defn reparse-string
  "If `i` is a string, pass it to `parser`; otherwise, return `i` unmodified."
  [parser i]
  (if (string? i)
    (parser i)
    i))

#_(defn break-reducer
    "Filter based on breaks"
    [out item]
    (cond (string? item) (conj out item)
          (= [:br] item) (cond (string? (last out)) (conj out item)
                               (= [:br] (last out)) (conj out item)
                               :else                out)
          (coll? item)   (cond (string? (last out)) (conj out item)
                               (= [:br] (last out)) (break-reducer (butlast out)
                                                                   item)
                               :else                (conj out item))))

#_(defn break-cleaner
    "Remove extraneous breaks"
    ([coll]
     (reduce break-reducer [] coll))
    ([coll lead]
     (reduce break-reducer [lead] coll)))

;; Overall Parser

(defn parse
  "Take org-mode data and parse it to hiccup notation"
  [data]
  (->> data
       doc-metadata
       (map (partial reparse-string headlines))
       fix-tree
       (insta/transform {:h clean-headline})
       #_(insta/transform {:section (fn [& stuff]
                                      (break-cleaner stuff :section))})
       #_rejoin-lines
       #_(insta/transform {:section (fn [& stuff]
                                      (rejoin-lines (concat [:section]
                                                            stuff)))})))


(defn parse-file
  "Read the given file path and parse it"
  [path]
  (parse (slurp path)))

(comment
  (parse-file (str (fs/home) "/todo/readme.org"))
  (parse-file (str (fs/home) "/todo/daily/2022-09-26.org"))
  (parse-file (str (fs/home) "/todo/daily/2022-09-27.org"))
  (parse-file (str (fs/home) "/todo/journal.org")))
