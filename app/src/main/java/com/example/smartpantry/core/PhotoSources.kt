package com.example.smartpantry.core


/** Catalogue art is a display fallback, not a new user file to upload. */
internal fun shoppingPhotoOverride(path: String?, catalogueImages: Set<String>): String? {
    if(path==null || path=="")return path
    if(path in catalogueImages)return null
    if(path.startsWith("https://",true) || path.startsWith("http://",true))return path
    if(path.startsWith("content://") || path.startsWith("file://") || path.startsWith("/"))return path
    // Resource names, resource:// URIs and Storage-relative paths are not local files.
    return null
}