# iCalendar parsing (biweekly) maps property classes to their parsers by class; keep them intact.
-keep class biweekly.** { *; }
-keep class com.github.mangstadt.vinnie.** { *; }
# biweekly's optional jCal/XML support references classes Android doesn't ship.
-dontwarn com.fasterxml.jackson.**
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.**
