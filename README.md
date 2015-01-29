# FindUnusedResources
Find and remove unused resources in an Android project

Removes the following resources defined in any .xml file located in any project/res/values* directory

	string, dimen, color, string-array, array, style

Removes the following resources located in any project/res/drawable* directory:

	.png, .xml

## Build

	cd src/
	javac FindUnusedResources.java

## Run

	java FindUnusedResources PATH
	- where PATH is the path to your Android project (should have AndroidManifest.xml file in it)
	- optionally, add "noprompt" after PATH to remove unused resources w/out prompting

## WARNING

By default, this program will by allow you to view unused resources before removing anything. However, to be same please sure to start with a clean workspace (ie: no outstanding changes) under source control or have a copy of the project backed up! That way, if it removes something you needed, you can always revert easily.

## DETAILS

Here's what this program is doing in steps.. see the source code for the low-level details!

### STEP 1 - build up a list of all resources used by the app

#### Index the contents of all .xml files in all res/values*/ folders
	
	eg:
	res/values/strings.xml
	    <string name="text_log_in">Sign In</string>
	    <string name="text_reset">Reset</string>

	res/values/colors.xml
	    <color name="dark_grey">#ACACAA</color>
    	<color name="white">#FFFFFF</color>
    	
    res/values/dimens.xml
	    <dimen name="text_8">8dp</dimen>
    	<dimen name="text_10">10dp</dimen>
   	
#### Index filenames in all res/drawable*/ folders

	eg:
	res/drawable/selector1.xml
	res/drawable-xxhdpi/icon.png

#### Index filenames in all res/layout*/ folders

	eg:
	res/layout/dialog1.xml
	res/layout/fragment1.xml
	
### STEP 2 - find number of uses for each of the indexed resources

#### search through all .java and .xml files in the project, looking for any uses of the indexed resources

There's a few different ways a resource can be referenced in Android.. here's what I'm looking for.

.java:

	R.<type>.<value> (where <type> could be "string", "dimen", "color", etc)
	R.id.<value>
	
.xml

	@<type>/<value> (where <type> could be "string", "dimen", "color", etc)
	@id/<value>
	- additionally, the following are looked at for <style>'s:
	   parent=<value>
	   @<value>.

### STEP 3 - remove or delete resources with no references

### STEP 4 - repeat step 2 & 3 until we've removed all unused resources

This step is necessary because a resource may be referenced by another resource. For example, an image could be referenced by a layout. If the layout isn't referenced anywhere, it'll be removed in the first pass. But, since the image was referenced, it won't be removed.

On the second pass, the unreferenced image will be removed.. and so on.

### OUTPUT

The app will print some statistics while it runs. See the example output below:

(UPDATE: output has changed slightly since I'm now prompting the user after each PASS) 

    Indexing resources...
    got 368 string resources
    got 98 dimen resources
    got 73 color resources
    got 2 string-array resources
    got 38 style resources
    got 125 layout resources
    got 297 drawable resources

    PASS 1...............................
    Removed 61 string resources
    Removed 5 dimen resources
    Removed 8 color resources
    Removed 57 drawable resources

    PASS 2..............................
    Removed 3 color resources
    Removed 3 drawable resources

    PASS 3...........................
    Removed 2 drawable resources

    PASS 4............................
    Removed 1 color resources

    PASS 5.............................
    DONE! Removed 80 TOTAL resources
    -> 12 color resources
    -> 61 string resources
    -> 62 drawable resources
    -> 5 dimen resources
