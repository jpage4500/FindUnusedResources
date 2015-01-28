import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Find and remove unused resources in an Android project.
 * Removes the following resources defined in any .xml file located in any <root>/res/values* directory:
 * - { string, dimen, color, string-array, array, style }
 * Removes the following resources located in any <root>/res/drawable* directory:
 * - { .png, .xml }
 */
public class FindUnusedResources {

    private static Map<String, AtomicInteger> mStringMap = new TreeMap<String, AtomicInteger>();
    private static Map<String, AtomicInteger> mDimenMap = new TreeMap<String, AtomicInteger>();
    private static Map<String, AtomicInteger> mColorMap = new TreeMap<String, AtomicInteger>();
    private static Map<String, AtomicInteger> mArrayMap = new TreeMap<String, AtomicInteger>();
    private static Map<String, AtomicInteger> mDrawableMap = new TreeMap<String, AtomicInteger>();
    private static Map<String, AtomicInteger> mLayoutMap = new TreeMap<String, AtomicInteger>();
    private static Map<String, AtomicInteger> mStylesMap = new TreeMap<String, AtomicInteger>();

    private static String USE_STRING = "string";
    private static String USE_DIMEN = "dimen";
    private static String USE_COLOR = "color";
    private static String USE_ARR = "string-array"; // name used for defining arrays
    private static String USE_ARR2 = "array"; // name used for referencing arrays
    private static String USE_DRAWABLE = "drawable";
    private static String USE_LAYOUT = "layout";
    private static String USE_STYLES = "style";

    private static String[] EXCLUDE_FILES = {"analytics.xml", "strings-generated.xml"};

    private static Map<String, Integer> mTotalRemovedMap = new HashMap<String, Integer>();

    private static long mLastUpdateMs;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Program to find and remove unused resources");
            System.out.println("usage: FindUnusedResources <path>");
            System.out.println("- where <path> is the path to an Android project (where AndroidManifest.xml exists)");
            System.out.println("");
            System.out.println("eg: java FindUnusedResources ~/working/AndroidProject");
            System.exit(0);
        }

        String root = args[0];

        File mainFile = new File(root + "/AndroidManifest.xml");
        if (mainFile.exists() == false) {
            System.out.println("file: " + mainFile + " does not exist!\nBase directory should point to an Android project.");
            System.exit(0);
        }

        File resDir = new File(root + "/res");

        System.out.println("Indexing resources...");

        // index contents of all .xml files in values*/ directory
        indexValues(resDir, false);

        // index all filenames in every /res/drawable*/ directory
        indexDrawables(resDir, false);

        // index all filenames in every /res/layout*/ directory
        indexLayout(resDir, false);

        System.out.println("got " + mStringMap.size() + " " + USE_STRING + " resources");
        System.out.println("got " + mDimenMap.size() + " " + USE_DIMEN + " resources");
        System.out.println("got " + mColorMap.size() + " " + USE_COLOR + " resources");
        System.out.println("got " + mArrayMap.size() + " " + USE_ARR + " resources");
        System.out.println("got " + mStylesMap.size() + " " + USE_STYLES + " resources");
        System.out.println("got " + mLayoutMap.size() + " " + USE_LAYOUT + " resources");
        System.out.println("got " + mDrawableMap.size() + " " + USE_DRAWABLE + " resources");

        // may need to loop a few times to find & delete all unused variables
        // for example, a drawable 'abc' may be referenced by a layout which isn't referenced in any code.
        // - the first pass will delete the layout and the second pass will delete the drawable
        int totalRemoved = 0;
        for (int i = 1; true; i++) {
            int numRemoved = findAndRemoveResources(root, i);
            if (numRemoved == 0) {
                break;
            }

            totalRemoved += numRemoved;
        }
        System.out.println("DONE! Removed " + totalRemoved + " TOTAL resources");

        Iterator<String> keyItor = mTotalRemovedMap.keySet().iterator();
        while (keyItor.hasNext()) {
            String key = keyItor.next();
            Integer value = mTotalRemovedMap.get(key);
            System.out.println("-> " + value + " " + key + " resources");
        }
    }

    private static int findAndRemoveResources(String root, int i) {

        File resDir = new File(root + "/res");

        // look at all files to determine if resources are still in use
        System.out.print("\nPASS " + i);
        searchFileForUse(new File(root + "/AndroidManifest.xml"));
        searchDirForUse(new File(root + "/src"));
        searchDirForUse(resDir);
        System.out.println();

        // delete files that aren't in use

        indexValues(resDir, true);
        indexDrawables(resDir, true);
        indexLayout(resDir, true);

        // pring and clear deleted resources from maps for next time through
        int totalRemoved = 0;
        totalRemoved += printUnused(mStringMap, USE_STRING);
        totalRemoved += printUnused(mDimenMap, USE_DIMEN);
        totalRemoved += printUnused(mColorMap, USE_COLOR);
        totalRemoved += printUnused(mArrayMap, USE_ARR);
        totalRemoved += printUnused(mStylesMap, USE_STYLES);
        totalRemoved += printUnused(mLayoutMap, USE_LAYOUT);
        totalRemoved += printUnused(mDrawableMap, USE_DRAWABLE);

        return totalRemoved;
    }

    private static void indexValues(File dir, boolean isDeleteMode) {
        File[] fileArr = dir.listFiles();
        for (File file : fileArr) {
            String filename = file.getName();
            if (file.isDirectory() && filename.startsWith("values")) {
                indexValues(file, isDeleteMode);
            } else if (filename.endsWith(".xml") && !isExcludedFile(filename)) {
                if (isDeleteMode) {
                    replaceFileContents(file);
                } else {
                    readFileContents(file);
                }
            }
        }
    }

    private static void indexDrawables(File dir, boolean isDeleteMode) {
        File[] fileArr = dir.listFiles();
        for (File file : fileArr) {
            String filename = file.getName();
            if (file.isDirectory() && filename.startsWith("drawable")) {
                indexDrawables(file, isDeleteMode);
            }
            // NOTE: drawables can be png files or xml files:
            // ie: background=@drawable/selector.xml
            else if (!file.isDirectory() && (filename.endsWith(".png") || filename.endsWith(".xml") && !isExcludedFile(filename))) {
                filename = filename.substring(0, filename.length() - 4);
                if (filename.endsWith(".9")) {
                    filename = filename.substring(0, filename.length() - 2);
                }

                if (isDeleteMode) {
                    AtomicInteger count = mDrawableMap.get(filename);
                    if (count != null && count.get() == 0) {
                        file.delete();
                    }
                } else {
                    if (mDrawableMap.containsKey(filename) == false) {
                        mDrawableMap.put(filename, new AtomicInteger());
                    }
                }
            }
        }
    }

    private static void indexLayout(File dir, boolean isDeleteMode) {
        File[] fileArr = dir.listFiles();
        for (File file : fileArr) {
            String filename = file.getName();
            if (file.isDirectory() && filename.startsWith("layout")) {
                indexLayout(file, isDeleteMode);
            } else if (!file.isDirectory() && filename.endsWith(".xml") && !isExcludedFile(filename)) {
                filename = filename.substring(0, filename.length() - 4);

                if (isDeleteMode) {
                    AtomicInteger count = mLayoutMap.get(filename);
                    if (count != null && count.get() == 0) {
                        file.delete();
                    }
                } else {
                    if (mLayoutMap.containsKey(filename) == false) {
                        mLayoutMap.put(filename, new AtomicInteger());
                    }
                }
            }
        }
    }

    private static boolean isExcludedFile(String filename) {
        for (String exclude : EXCLUDE_FILES) {
            if (filename.equals(exclude)) {
                return true;
            }
        }
        return false;
    }

    private static void searchDirForUse(File dir) {
        // now, look through all .java and .xml files to find uses
        File[] fileArr = dir.listFiles();
        for (File file : fileArr) {
            if (file.isDirectory()) {
                searchDirForUse(file);
            } else {
                String filename = file.getName();
                if (filename.endsWith(".xml") || filename.endsWith(".java")) {
                    // System.out.println("seacrhing: " + file);
                    searchFileForUse(file);

                    long timeMs = System.currentTimeMillis();
                    if (timeMs - mLastUpdateMs >= 400) {
                        System.out.print(".");
                        mLastUpdateMs = timeMs;
                    }
                }
            }
        }
    }

    private static int printUnused(Map<String, AtomicInteger> map, String text) {
        int count = 0;
        StringBuffer unused = new StringBuffer();
        Iterator<String> it = map.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            AtomicInteger value = map.get(key);
            if (value.get() == 0) {
                // UNUSED
                count++;
                unused.append(key).append('\n');
                // delete this key
                it.remove();
            } else {
                // USED - reset back to 0
                value.set(0);
            }
        }
        if (count > 0) {
            System.out.println("Removed " + count + " " + text + " resources");
            System.out.println("UNUSED: " + text + "\n" + unused.toString());

            // track # of items removed for each <text>
            Integer prevTotal = mTotalRemovedMap.get(text);
            int newTotal = count;
            if (prevTotal != null) {
                newTotal += prevTotal.intValue();
            }
            mTotalRemovedMap.put(text, Integer.valueOf(newTotal));
        }

        return count;
    }

    private static void readFileContents(File file) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }

                // each line in an xml file can contain at most 1 of the below
                boolean isFound;
                isFound = addLineEntry(line, mStringMap, createBeginTag(USE_STRING));
                if (!isFound) {
                    isFound = addLineEntry(line, mDimenMap, createBeginTag(USE_DIMEN));
                }
                if (!isFound) {
                    isFound = addLineEntry(line, mColorMap, createBeginTag(USE_COLOR));
                }
                if (!isFound) {
                    isFound = addLineEntry(line, mArrayMap, createBeginTag(USE_ARR));
                }
                if (!isFound) {
                    isFound = addLineEntry(line, mStylesMap, createBeginTag(USE_STYLES));
                }
            }
        } catch (Exception e) {
            System.out.println("readFileContents: Error reading file: " + file + ", " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private static String createBeginTag(String USE_STRING) {
        return "<" + USE_STRING + " name=\"";
    }

    private static boolean addLineEntry(String line, Map<String, AtomicInteger> map, String key) {
        int pos = line.indexOf(key);
        if (pos >= 0) {
            String value = line.substring(pos + key.length());
            int p2 = value.indexOf("\"");
            if (p2 > 0) {
                value = value.substring(0, p2);
                if (map.containsKey(value) == false) {
                    map.put(value, new AtomicInteger(0));
                    //System.out.println("adding: " + key + value + "\"");
                }
                return true;
            }
        }
        return false;
    }

    /**
     * check if given key is in the line AND that the value associated is UNUSED
     */
    private static boolean checkLineEntry(String line, Map<String, AtomicInteger> map, String key) {
        int pos = line.indexOf(key);
        if (pos >= 0) {
            String value = line.substring(pos + key.length());
            int p2 = value.indexOf("\"");
            if (p2 > 0) {
                value = value.substring(0, p2);
                AtomicInteger count = map.get(value);
                //System.out.println("checkLine: " + value + ", count: " + count);
                return (count != null && count.get() == 0);
            }
        }
        return false;
    }

    private static void searchFileForUse(File file) {
        boolean isJava = file.getName().endsWith(".java");
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }

                // ignore commented out lines
                //                if (isJava && line.trim().startsWith("//")) {
                //                    break;
                //                }

                searchLineForUse(isJava, line, mStringMap, USE_STRING);
                searchLineForUse(isJava, line, mDimenMap, USE_DIMEN);
                searchLineForUse(isJava, line, mColorMap, USE_COLOR);
                searchLineForUse(isJava, line, mArrayMap, USE_ARR2);
                searchLineForUse(isJava, line, mDrawableMap, USE_DRAWABLE);
                searchLineForUse(isJava, line, mStylesMap, USE_STYLES);
                searchLineForUse(isJava, line, mLayoutMap, USE_LAYOUT);
            }
        } catch (Exception e) {
            System.out.println("searchFileForUse: Error reading file: " + file + ", " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private static boolean searchLineForUse(boolean isJava, String line, Map<String, AtomicInteger> map, String key) {
        String searchFor;
        String searchFor2 = null; // special case; style element can have a parent=value
        String searchFor3 = null; // special case; style element can have a parent.name
        for (String value : map.keySet()) {
            if (isJava) {
                String convertedValue = value;
                // special case: in java files, dot is replaced with underscore
                // eg: Parent.Style is referenced as Parent_Style
                if (convertedValue.indexOf('.') > 0) {
                    convertedValue = value.replace('.', '_');
                }
                searchFor = "R." + key + "." + convertedValue;

                // special case: check reference to R.id.value
                searchFor2 = "R.id." + convertedValue;
            } else {
                searchFor = "@" + key + "/" + value; // @string/value
                if (key.equals(USE_STYLES)) {
                    searchFor2 = "parent=\"" + value + "\"";
                    searchFor3 = "\"" + value + ".";
                }
            }

            int stPos = 0;
            while (true) {
                int pos = line.indexOf(searchFor, stPos);
                if (pos >= 0) {
                    boolean isFound = true;
                    if (pos + searchFor.length() < line.length()) {
                        // need to check next character. can be letter/digit/_/. which means we didn't find this key
                        char nextChar = line.charAt(pos + searchFor.length());
                        if (nextChar == '_' || nextChar == '.' || Character.isLetterOrDigit(nextChar)) {
                            isFound = false;

                            // special case: <searchFor> can be found later on in the same line. check 1 more time..
                            // eg: ? R.drawable.myfiles_file_mp4_thumb_lock : R.drawable.myfiles_file_mp4_thumb
                            stPos = pos + 1;
                            continue;
                        }
                    }
                    if (isFound) {
                        AtomicInteger count = map.get(value);
                        count.addAndGet(1);
                        // another variable could be used in the same Java line
                        if (!isJava) {
                            return true;
                        }
                    }
                }
                break;
            }

            // special case; style element can have a parent=value
            // eg: <style name="intro_text3" parent="intro_text_base">
            if (searchFor2 != null && line.indexOf(searchFor2) >= 0) {
                AtomicInteger count = map.get(value);
                count.addAndGet(1);
                return true;
            }

            // special case; style element can have a parent.value
            // <style name="DialogButton">
            // ...
            // <style name="DialogButton.Left">
            if (searchFor3 != null && line.indexOf(searchFor3) >= 0) {
                AtomicInteger count = map.get(value);
                count.addAndGet(1);
                return true;
            }

        }
        return false;
    }

    private static void replaceFileContents(File file) {
        StringBuffer sb = new StringBuffer();
        int numLinesDeleted = 0;
        String deleteUntilTag = null;
        BufferedReader br = null;
        BufferedWriter bw = null;
        try {
            br = new BufferedReader(new FileReader(file));
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }

                boolean isFound = false;

                // check if we're looking for an end tag
                if (deleteUntilTag != null) {
                    // delete this line no matter what
                    isFound = true;
                    if (line.indexOf(deleteUntilTag) >= 0) {
                        // found end tag
                        deleteUntilTag = null;
                    }
                }

                // each line in the xml file should only contain at most 1 of
                // the below entries (no need to look for all)
                if (!isFound) {
                    isFound = checkLineEntry(line, mStringMap, createBeginTag(USE_STRING));
                }
                if (!isFound) {
                    isFound = checkLineEntry(line, mDimenMap, createBeginTag(USE_DIMEN));
                }
                if (!isFound) {
                    isFound = checkLineEntry(line, mColorMap, createBeginTag(USE_COLOR));
                }

                // NOTE: the following entries aren't always 1-line :(
                if (!isFound) {
                    isFound = checkLineEntry(line, mArrayMap, createBeginTag(USE_ARR));
                    if (isFound) {
                        deleteUntilTag = "</" + USE_ARR + ">";
                    }
                }

                if (!isFound) {
                    isFound = checkLineEntry(line, mStylesMap, createBeginTag(USE_STYLES));
                    if (isFound) {
                        deleteUntilTag = "</" + USE_STYLES + ">";
                    }
                }

                // check if end tag is on same line as begin tag
                if (deleteUntilTag != null && line.contains(deleteUntilTag)) {
                    deleteUntilTag = null;
                }

                // if entry was found - remove it; otherwise, keep it
                if (!isFound) {
                    sb.append(line).append('\n');
                } else {
                    numLinesDeleted++;
                }
            }

            if (numLinesDeleted > 0 && sb.length() > 0) {
                // replace file with filtered version
                bw = new BufferedWriter(new FileWriter(file));
                bw.write(sb.toString());
                bw.close();
            }

        } catch (Exception e) {
            System.out.println("replaceFileContents: Error reading file: " + file + ", " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
                if (bw != null) {
                    bw.close();
                }
            } catch (IOException e) {
            }
        }
    }
}
