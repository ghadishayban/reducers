# reducible collections, transducing ops and other fast things #

Relies on the latest Clojure 1.7

### reducible variants of core ###

* repeatedly
* iterate
* range

### ops that use reduce/transduce as a base ###

* count
* some
* select-keys
* zipmap (uses iterators)

### new ops ###

* any (aka find-first or search)
* yield-first (reduces until first truthy value.  underlies some & any)

* transiterate
Takes an Iterable and a transducer and gives you back an Iterable. Like LazyTransformer but for Iterables
