<?xml version="1.0" encoding="utf-8"?>

<LinearLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        android:id="@+id/about"
        android:orientation="vertical"
        android:layout_width="match_parent"
        android:layout_height="match_parent">
    <ImageView android:id="@+id/mumble_title"
               android:src="@drawable/mumble_title"
               android:layout_height="wrap_content"
               android:layout_width="match_parent" />

    <TextView
            android:id="@+id/aboutMumbleText"
            android:text="@string/aboutMumbleText"
            android:textSize="26sp"
            android:layout_height="wrap_content"
            android:gravity="center"
            android:layout_width="match_parent" />
    <LinearLayout
            android:layout_height="wrap_content"
            android:layout_width="fill_parent">
        <TextView
                android:id="@+id/aboutMumbleBuild"
                android:text="@string/aboutMumbleBuild"
                android:textSize="16sp"
                android:layout_height="wrap_content"
                android:gravity="center"
                android:layout_width="match_parent"
                android:paddingLeft="13sp"
                android:paddingTop="15sp"/>
    </LinearLayout>
    <TextView
            android:id="@+id/aboutMumbleURL"
            android:text="@string/aboutGitHubForkURL"
            android:textSize="16sp"
            android:layout_height="wrap_content"
            android:gravity="center"
            android:layout_width="match_parent"
            android:paddingLeft="13sp"
            android:paddingTop="15sp"/>
    <TextView
            android:id="@+id/aboutDisclaimer"
            android:text="@string/aboutDisclaimer"
            android:textSize="16sp"
            android:layout_height="wrap_content"
            android:gravity="left"
            android:layout_width="match_parent"
            android:paddingLeft="13sp"
            android:paddingTop="15sp"/>
    </LinearLayout>

