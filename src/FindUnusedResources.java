import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
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

    private static final int ACTION_EXIT = 4;
    private static final int ACTION_PRINT_ALL = 3;
    private static final int ACTION_PRINT_UNUSED = 1;
    private static final int ACTION_DELETE = 2;

    // each map below contains ALL indexed resources for that particular type (string/color/etc) and a reference count
    private static Map<String, AtomicInteger> mStringMap = new TreeMap<String, AtomicInteger>();
    private static Map<String, AtomicInteger> mDimenMap = new TreeMap<String, AtomicInteger>();
    private static Map<String, AtomicInteger> mColorMap = new TreeMap<String, AtomicInteger>();
    private static Map<String, AtomicInteger> mArrayMap = new TreeMap<String, AtomicInteger>();
    private static Map<String, AtomicInteger> mDrawableMap = new TreeMap<String, AtomicInteger>();
    private static Map<String, AtomicInteger> mLayoutMap = new TreeMap<String, AtomicInteger>();
    private static Map<String, AtomicInteger> mStylesMap = new TreeMap<String, AtomicInteger>();

    // what resources we're looking for..
    private static String USE_STRING = "string";
    private static String USE_DIMEN = "dimen";
    private static String USE_COLOR = "color";
    private static String USE_ARR = "string-array";
    private static String USE_ARR2 = "array";
    private static String USE_DRAWABLE = "drawable";
    private static String USE_LAYOUT = "layout";
    private static String USE_STYLES = "style";

    private static String[] EXCLUDE_FILES = {"analytics.xml"};

    private static Map<String, Integer> mTotalRemovedMap = new HashMap<String, Integer>();

    private static long mLastUpdateMs;

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(0);
        }

        String root = args[0];

        boolean promptUser = true;
        // check for "noprompt"
        if (args.length >= ACTION_DELETE && args[1].equalsIgnoreCase("noprompt")) {
            promptUser = false;
        }

        File mainFile = new File(root + "/AndroidManifest.xml");
        if (mainFile.exists() == false) {
            System.out.println("file: " + mainFile + " does not exist!\nBase directory should point to an Android project.");
            printUsage();
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
            // search for unused resources..
            int unused = findUnusedResources(root, i);
            if (unused == 0) {
                // nothing to do!
                break;
            }

            int numRemoved = 0;

            // prompt next action..
            while (true) {
                int command;
                if (promptUser) {
                    command = promptNext();
                } else {
                    command = ACTION_DELETE;
                }

                if (command == ACTION_PRINT_UNUSED) {
                    printResources(true, false);
                } else if (command == ACTION_DELETE) {
                    numRemoved = deleteUnusedResources(root, i);
                    // break out of loop and continue search..
                    break;
                }
                if (command == ACTION_PRINT_ALL) {
                    printResources(false, false);
                } else if (command == ACTION_EXIT) {
                    // STOP & exit!
                    return;
                }
            }

            if (numRemoved == 0) {
                // all DONE!
                break;
            }

            totalRemoved += numRemoved;
        }

        if (totalRemoved > 0) {
            System.out.println("DONE! Removed " + totalRemoved + " TOTAL resources");

            Iterator<String> keyItor = mTotalRemovedMap.keySet().iterator();
            while (keyItor.hasNext()) {
                String key = keyItor.next();
                Integer value = mTotalRemovedMap.get(key);
                System.out.println("-> " + value + " " + key + " resources");
            }
        }
    }

    private static void printUsage() {
        System.out.println("Program to find and remove unused resources");
        System.out.println("usage: FindUnusedResources <path>");
        System.out.println("- where <path> is the path to an Android project (where AndroidManifest.xml exists)");
        System.out.println("");
        System.out.println("- optionally, add \"noprompt\" after <path> to remove unused w/out prompting");
        System.out.println("eg: java FindUnusedResources ~/working/AndroidProject");
        System.out.println("eg: java FindUnusedResources ~/working/AndroidProject noprompt");
    }

    private static int promptNext() {
        //  prompt the user to enter their name
        System.out.println("");
        System.out.println("Select Option:");
        System.out.println(ACTION_PRINT_UNUSED + ") show UNUSED resources");
        System.out.println(ACTION_DELETE + ") DELETE unused resources");
        System.out.println(ACTION_PRINT_ALL + ") show ALL indexed resources & usage counts");
        System.out.println(ACTION_EXIT + ") exit");

        BufferedReader br = null;
        String choice = null;
        try {
            //  open up standard input
            br = new BufferedReader(new InputStreamReader(System.in));
            choice = br.readLine();

            return Integer.parseInt(choice);
        } catch (IOException ioe) {
            System.out.println("> IOException: " + choice);
            ioe.printStackTrace();
        } catch (NumberFormatException nfe) {
            System.out.println("> invalid choice: " + choice);
        }
        return 0;
    }

    private static int findUnusedResources(String root, int pass) {
        File resDir = new File(root + "/res");

        System.out.print("\nPASS " + pass);

        // search through AndroidManifext.xml
        searchFileForUse(new File(root + "/AndroidManifest.xml"));

        // search through all JAVA files in /src directory
        searchDirForUse(new File(root + "/src"));

        // search through all XML files in /res directory
        searchDirForUse(resDir);

        // done searching
        System.out.println();

        // print out summary for this pass
        return printResources(true, true);
    }

    private static int deleteUnusedResources(String root, int i) {
        File resDir = new File(root + "/res");

        // delete files that aren't in use
        indexValues(resDir, true);
        indexDrawables(resDir, true);
        indexLayout(resDir, true);

        // pring and clear deleted resources from maps for next time through
        int totalRemoved = 0;
        totalRemoved += resetCounters(mStringMap, USE_STRING);
        totalRemoved += resetCounters(mDimenMap, USE_DIMEN);
        totalRemoved += resetCounters(mColorMap, USE_COLOR);
        totalRemoved += resetCounters(mArrayMap, USE_ARR);
        totalRemoved += resetCounters(mStylesMap, USE_STYLES);
        totalRemoved += resetCounters(mLayoutMap, USE_LAYOUT);
        totalRemoved += resetCounters(mDrawableMap, USE_DRAWABLE);

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
                    filename = filename.substring(0, filename.length() - ACTION_DELETE);
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

    private static int printResources(boolean showUnusedOnly, boolean showSummaryOnly) {
        int total = 0;
        total += printResources(mStringMap, USE_STRING, showUnusedOnly, showSummaryOnly);
        total += printResources(mDimenMap, USE_DIMEN, showUnusedOnly, showSummaryOnly);
        total += printResources(mColorMap, USE_COLOR, showUnusedOnly, showSummaryOnly);
        total += printResources(mArrayMap, USE_ARR, showUnusedOnly, showSummaryOnly);
        total += printResources(mStylesMap, USE_STYLES, showUnusedOnly, showSummaryOnly);
        total += printResources(mLayoutMap, USE_LAYOUT, showUnusedOnly, showSummaryOnly);
        total += printResources(mDrawableMap, USE_DRAWABLE, showUnusedOnly, showSummaryOnly);
        return total;
    }

    private static int printResources(Map<String, AtomicInteger> map, String text, boolean showUnusedOnly, boolean showSummaryOnly) {
        int count = 0;
        StringBuffer unused = new StringBuffer();
        Iterator<String> it = map.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            AtomicInteger value = map.get(key);
            if (value == null) {
                continue;
            }
            if (showUnusedOnly && value.get() == 0) {
                // UNUSED RESOURCE
                count++;
                if (!showSummaryOnly) {
                    unused.append(key).append('\n');
                }
            } else if (!showUnusedOnly) {
                count++;
                if (!showSummaryOnly) {
                    unused.append(key + ", " + value.get()).append('\n');
                }
            }
        }

        if (count > 0) {
            if (showUnusedOnly) {
                System.out.println("found " + count + " unused " + text + " resources");
            } else {
                System.out.println("showing " + count + " " + text + " resources:");
                System.out.println("<resource>, <# of references>");
                System.out.println("-----------------------------");
            }

            if (!showSummaryOnly) {
                System.out.println(unused.toString());
            }
        }

        return count;
    }

    private static int resetCounters(Map<String, AtomicInteger> map, String text) {
        int count = 0;
        Iterator<String> it = map.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            AtomicInteger value = map.get(key);
            if (value.get() == 0) {
                // UNUSED RESOURCE
                count++;
                // delete this key
                it.remove();
            } else {
                // USED - reset back to 0
                value.set(0);
            }
        }
        if (count > 0) {
            System.out.println("REMOVED " + count + " " + text + " resources");
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
                    // done reading file
                    break;
                }

                // ignore commented out lines
                if (isJava && line.trim().startsWith("//")) {
                    continue;
                }

                // search line for a reference to one of the indexed resources
                // NOTE: I'm expecting at most a line can only contain a reference to a single resource type (string/color/etc)
                // > Once one is found - we can save time by skiping searching for others on the same line
                // Multiple references for the same time are checked:
                // ex: int resId = (isSomething ? R.string.one : R.string.two);
                boolean isMatch = false;
                isMatch = searchLineForUse(isJava, line, mStringMap, USE_STRING);
                if (!isMatch) {
                    searchLineForUse(isJava, line, mDimenMap, USE_DIMEN);
                }
                if (!isMatch) {
                    isMatch = searchLineForUse(isJava, line, mColorMap, USE_COLOR);
                }
                if (!isMatch) {
                    isMatch = searchLineForUse(isJava, line, mArrayMap, USE_ARR2);
                }
                if (!isMatch) {
                    isMatch = searchLineForUse(isJava, line, mDrawableMap, USE_DRAWABLE);
                }
                if (!isMatch) {
                    isMatch = searchLineForUse(isJava, line, mStylesMap, USE_STYLES);
                }
                if (!isMatch) {
                    isMatch = searchLineForUse(isJava, line, mLayoutMap, USE_LAYOUT);
                }
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

    private static boolean searchLineForUse(boolean isJava, String line, Map<String, AtomicInteger> map, String type) {
        String searchFor; // primary use case (ie: R.string.value)
        String searchFor2 = null; // secondary use case (ie: R.id.value)

        boolean isFound = false;

        // check each indexed value in map
        for (String value : map.keySet()) {
            if (isJava) {
                String convertedValue = value;
                // special case: in java files, dot is replaced with underscore
                // eg: Parent.Style is referenced as Parent_Style
                if (convertedValue.indexOf('.') > 0) {
                    convertedValue = value.replace('.', '_');
                }
                searchFor = "R." + type + "." + convertedValue; // R.string.value
                searchFor2 = "R.id." + convertedValue; // R.id.value
            } else {
                // XML file
                searchFor = "@" + type + "/" + value; // @string/value
                searchFor2 = "@id/" + value; //  @id/value
            }

            isFound = searchLineForUseWithKey(line, map, searchFor);
            if (!isFound && searchFor2 != null) {
                isFound = searchLineForUseWithKey(line, map, searchFor2);
            }

            if (!isFound && !isJava && map == mStylesMap) {
                // special case: styles can reference a parent 3 ways in XML file:
                // 1) parent=
                // <style name="SquareButtonStyle">
                // <style name="GreenSquareButtonStyle" parent="@style/SquareButtonStyle">
                if (line.indexOf("parent=\"@" + type + "/" + value + "\"") >= 0) {
                    isFound = true;
                }
                // 2) parent.child
                // <style name="DialogButton">
                // <style name="DialogButton.Left">
                if (!isFound && line.indexOf("\"" + value + ".") >= 0) {
                    isFound = true;
                }
                // 3) parent=
                // <style name="SquareButtonStyle">
                // <style name="GreenSquareButtonStyle" parent="SquareButtonStyle">
                if (line.indexOf("parent=\"" + value + "\"") >= 0) {
                    isFound = true;
                }
            }

            if (isFound) {
                // incremement value reference
                AtomicInteger count = map.get(value);
                count.addAndGet(1);
            }
        }
        return isFound;
    }

    private static boolean searchLineForUseWithKey(String line, Map<String, AtomicInteger> map, String searchFor) {
        int stPos = 0;
        boolean isFound = false;
        // while() loop is to handle multiple resources referenced on a single line
        // eg: ? R.drawable.myfiles_file_mp4_thumb_lock : R.drawable.myfiles_file_mp4_thumb
        while (true) {
            // check if string exists in line
            int pos = line.indexOf(searchFor, stPos);
            if (pos < 0) {
                // not found!
                break;
            }

            isFound = true;

            if (pos + searchFor.length() < line.length()) {
                // need to check next character. can be letter/digit/_/. which means we didn't find this key
                char nextChar = line.charAt(pos + searchFor.length());
                if (nextChar == '_' || nextChar == '.' || Character.isLetterOrDigit(nextChar)) {
                    // false positive.. keep searching rest of line
                    isFound = false;

                    // special case: <searchFor> can be found later on in the same line. check 1 more time..
                    // eg: ? R.drawable.myfiles_file_mp4_thumb_lock : R.drawable.myfiles_file_mp4_thumb
                    stPos = pos + 1;
                    continue;
                }
            }

            // only want to loop once.. while() is for case above
            break;
        }

        return isFound;
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
                    // could be multi-line.. but, typically not
                    if (isFound && !line.contains("</string>") && !line.contains("/>")) {
                        deleteUntilTag = "</" + USE_STRING + ">";
                    }
                }

                if (!isFound) {
                    isFound = checkLineEntry(line, mDimenMap, createBeginTag(USE_DIMEN));
                }
                if (!isFound) {
                    isFound = checkLineEntry(line, mColorMap, createBeginTag(USE_COLOR));
                }

                // NOTE: the following entries aren't always 1-line

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
