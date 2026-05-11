package com.elbaroudi.localvpn.utils

class DomainTrie {
    private class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        var isEndOfDomain = false
    }

    private val root = TrieNode()

    /**
     * Inserts a domain into the Trie.
     * Stores characters in REVERSE order to facilitate suffix matching.
     * e.g., "google.com" is stored as 'm' -> 'o' -> 'c' -> '.' -> 'e' ...
     */
    fun insert(domain: String) {
        if (domain.isBlank()) return
        
        var current = root
        // Iterate backwards
        for (i in domain.length - 1 downTo 0) {
            val char = domain[i]
            current = current.children.computeIfAbsent(char) { TrieNode() }
        }
        current.isEndOfDomain = true
    }

    /**
     * Checks if the given domain matches any blocked domain in the Trie.
     * Matches if:
     * 1. Exact match (e.g. domain is "google.com" and "google.com" is in Trie)
     * 2. Suffix match (e.g. domain is "ads.google.com" and "google.com" is in Trie)
     */
    fun matches(domain: String): Boolean {
        if (domain.isBlank()) return false
        
        var current = root
        // Iterate backwards
        for (i in domain.length - 1 downTo 0) {
            val char = domain[i]
            
            // Navigate down the trie
            val nextNode = current.children[char]
            
            if (nextNode == null) {
                // Mismatch, this specific path doesn't exist.
                return false
            }
            
            current = nextNode
            
            // Check if we found a blocked domain marker
            if (current.isEndOfDomain) {
                // We found a blocked entry ending here.
                // It's a match IF:
                // 1. We consumed the entire query domain (Exact match)
                // 2. The next character in our backward traversal (which is effectively the prev char in string) is a dot '.'
                //    Example: Query "ads.google.com". Backwards: m-o-c-.-e-l-g-o-o-g-[MATCH]. Next char is '.'.
                //    This prevents "agoogle.com" from matching "google.com".
                
                if (i == 0) {
                    return true // Exact match
                }
                
                // If we are not at the start of the string, the "next" char (backward) must be a dot
                if (domain[i - 1] == '.') {
                    return true // Subdomain match
                }
            }
        }
        
        return false
    }

    fun clear() {
        root.children.clear()
        root.isEndOfDomain = false
    }
}
