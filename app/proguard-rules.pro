# Libraries supply their own consumer rules. Avoid blanket -dontwarn rules:
# missing classes must remain visible to R8 and fail the release build.

# SQLCipher calls Java members from native code; preserve their JNI names.
-keep class net.zetetic.database.sqlcipher.** { *; }

# Keep the small expression engine used by the calculator.
-keep class net.objecthunter.exp4j.** { *; }
