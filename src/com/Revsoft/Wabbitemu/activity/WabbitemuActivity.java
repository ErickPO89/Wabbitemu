package com.Revsoft.Wabbitemu.activity;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.DrawerLayout.DrawerListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.Revsoft.Wabbitemu.CalcInterface;
import com.Revsoft.Wabbitemu.R;
import com.Revsoft.Wabbitemu.SkinBitmapLoader;
import com.Revsoft.Wabbitemu.calc.CalculatorManager;
import com.Revsoft.Wabbitemu.calc.FileLoadedCallback;
import com.Revsoft.Wabbitemu.fragment.EmulatorFragment;
import com.Revsoft.Wabbitemu.utils.AnalyticsConstants.UserActionActivity;
import com.Revsoft.Wabbitemu.utils.AnalyticsConstants.UserActionEvent;
import com.Revsoft.Wabbitemu.utils.ErrorUtils;
import com.Revsoft.Wabbitemu.utils.IntentConstants;
import com.Revsoft.Wabbitemu.utils.PreferenceConstants;
import com.Revsoft.Wabbitemu.utils.StorageUtils;
import com.Revsoft.Wabbitemu.utils.UserActivityTracker;

public class WabbitemuActivity extends Activity {
	private static final int LOAD_FILE_CODE = 1;
	private static final int SETUP_WIZARD = 2;
	private static final String DEFAULT_FILE_REGEX = "\\.(rom|sav|[7|8][2|3|x|c|5|6][b|c|d|g|i|k|l|m|n|p|q|s|t|u|v|w|y|z])$";

	private enum MainMenuItem {
		LOAD_FILE_MENU_ITEM(0),
		WIZARD_MENU_ITEM(1),
		RESET_MENU_ITEM(2),
		SCREENSHOT_MENU_ITEM(3),
		SETTINGS_MENU_ITEM(4),
		ABOUT_MENU_ITEM(5);

		private final int mPosition;

		private MainMenuItem(final int position) {
			mPosition = position;
		}

		public static MainMenuItem fromPosition(final int position) {
			for (final MainMenuItem item : values()) {
				if (item.mPosition == position) {
					return item;
				}
			}

			return null;
		}
	}

	private final UserActivityTracker mUserActivityTracker = UserActivityTracker.getInstance();
	private final CalculatorManager mCalcManager = CalculatorManager.getInstance();
	private final SkinBitmapLoader mSkinLoader = SkinBitmapLoader.getInstance();

	private EmulatorFragment mEmulatorFragment;
	private DrawerLayout mDrawerLayout;
	private ListView mDrawerList;
	private boolean mWasUserLaunched;

	private void handleFile(File f, Runnable runnable) {
		mUserActivityTracker.reportUserAction(UserActionActivity.MAIN_ACTIVITY,
				UserActionEvent.SEND_FILE,
				f.getAbsolutePath());

		mEmulatorFragment.handleFile(f, runnable);
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		Log.i("WabbitemuActivity", "Wabbit start");
		super.onCreate(savedInstanceState);
		mUserActivityTracker.initialize(this, getString(R.string.mintKey));
		mCalcManager.initialize(this);
		mSkinLoader.initialize(this);

		mUserActivityTracker.reportActivityStart(this);
		final File cacheDir = getCacheDir();
		CalcInterface.SetCacheDir(cacheDir.getAbsolutePath());
		final String fileName = getLastRomSetting();
		final Runnable runnable = getLaunchRunnable();
		if (fileName != null) {
			final File file = new File(fileName);
			mCalcManager.addRomLoadListener(new FileLoadedCallback() {

				@Override
				public void onFileLoaded(boolean wasSuccessful) {
					if (!wasSuccessful) {
						runnable.run();
					}

					mCalcManager.removeRomLoadListener(this);
				}
			});
			mCalcManager.loadRomFile(file);
		}

		toggleHideyBar();
		setContentView(R.layout.main);
		mEmulatorFragment = (EmulatorFragment) getFragmentManager().findFragmentById(R.id.content_frame);
		attachMenu();

		if (isFirstRun()) {
			mWasUserLaunched = false;
			final Intent wizardIntent = new Intent(this, WizardActivity.class);
			startActivityForResult(wizardIntent, SETUP_WIZARD);
			return;
		}

		// we expect an absolute filename
		final int lastRomModel = getLastRomModel();
		if (lastRomModel >= 0) {
			mSkinLoader.loadSkinAndKeymap(lastRomModel);
		} else if (fileName == null || fileName.equals("")) {
			runnable.run();
		}
	}

	@Override
	public void onStop() {
		super.onStop();

		mUserActivityTracker.reportActivityStop(this);
	}

	private void attachMenu() {
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mDrawerList = (ListView) findViewById(R.id.left_drawer);
		final String[] menuItems = getResources().getStringArray(R.array.menu_array);

		mDrawerList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, menuItems));
		mDrawerLayout.setScrimColor(Color.parseColor("#DD000000"));
		mDrawerList.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				handleMenuItem(MainMenuItem.fromPosition(position));
			}
		});
		mDrawerLayout.setDrawerListener(new DrawerListener() {

			@Override
			public void onDrawerStateChanged(final int arg0) {
				// no-op
			}

			@Override
			public void onDrawerSlide(final View drawerView, final float slideOffset) {
				mDrawerLayout.bringChildToFront(drawerView);
				mDrawerLayout.requestLayout();
			}

			@Override
			public void onDrawerOpened(final View arg0) {
				mUserActivityTracker.reportUserAction(UserActionActivity.MAIN_ACTIVITY, UserActionEvent.OPEN_MENU);
			}

			@Override
			public void onDrawerClosed(final View arg0) {
				// no-op
			}
		});
	}

	private Runnable getLaunchRunnable() {
		return new Runnable() {

			@Override
			public void run() {
				final Intent wizardIntent = new Intent(WabbitemuActivity.this, WizardActivity.class);
				startActivityForResult(wizardIntent, SETUP_WIZARD);
			}
		};
	}

	private String getLastRomSetting() {
		final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		return sharedPrefs.getString(PreferenceConstants.ROM_PATH.toString(), null);
	}

	private int getLastRomModel() {
		final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		return sharedPrefs.getInt(PreferenceConstants.ROM_MODEL.toString(), -1);
	}

	private boolean isFirstRun() {
		final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		return sharedPrefs.getBoolean(PreferenceConstants.FIRST_RUN.toString(), true);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		switch (requestCode) {
		case LOAD_FILE_CODE:
			if (resultCode == RESULT_OK) {
				final String fileName = data.getStringExtra(IntentConstants.FILENAME_EXTRA_STRING);
				handleFile(new File(fileName), new Runnable() {

					@Override
					public void run() {
						ErrorUtils.showErrorDialog(WabbitemuActivity.this, R.string.errorLink);

						mUserActivityTracker.reportUserAction(UserActionActivity.MAIN_ACTIVITY,
								UserActionEvent.SEND_FILE_ERROR, fileName);
					}
				});
			}
			break;
		case SETUP_WIZARD:
			if (resultCode == RESULT_OK) {
				final String fileName = data.getStringExtra(IntentConstants.FILENAME_EXTRA_STRING);
				handleFile(new File(fileName), getLaunchRunnable());

				if (isFirstRun()) {
					final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
					final SharedPreferences.Editor editor = sharedPrefs.edit();
					editor.putBoolean(PreferenceConstants.FIRST_RUN.toString(), false);
					editor.commit();

					mDrawerLayout.openDrawer(mDrawerList);
				}
			} else if (!mWasUserLaunched) {
				finish();
			}
			break;
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu) {
		if (mDrawerLayout.isDrawerOpen(mDrawerList)) {
			mDrawerLayout.closeDrawer(mDrawerList);
		} else {
			mDrawerLayout.openDrawer(mDrawerList);
		}

		return false;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		final MainMenuItem position;
		switch (item.getItemId()) {
		case R.id.aboutMenuItem:
			position = MainMenuItem.ABOUT_MENU_ITEM;
			break;
		case R.id.settingsMenuItem:
			position = MainMenuItem.SETTINGS_MENU_ITEM;
			break;
		case R.id.resetMenuItem:
			position = MainMenuItem.RESET_MENU_ITEM;
			break;
		case R.id.rerunWizardMenuItem:
			position = MainMenuItem.WIZARD_MENU_ITEM;
			break;
		case R.id.loadFileMenuItem:
			position = MainMenuItem.LOAD_FILE_MENU_ITEM;
			break;
		default:
			return super.onOptionsItemSelected(item);
		}

		return handleMenuItem(position);
	}

	private boolean handleMenuItem(final MainMenuItem position) {
		mDrawerLayout.closeDrawer(mDrawerList);

		mUserActivityTracker.reportUserAction(UserActionActivity.MAIN_ACTIVITY,
				UserActionEvent.MENU_ITEM_SELECTED,
				position.toString());

		switch (position) {
		case SETTINGS_MENU_ITEM:
			launchSettings();
			return true;
		case RESET_MENU_ITEM:
			resetCalc();
			return true;
		case SCREENSHOT_MENU_ITEM:
			screenshotCalc();
			return true;
		case WIZARD_MENU_ITEM:
			launchWizard();
			return true;
		case LOAD_FILE_MENU_ITEM:
			launchBrowse();
			return true;
		case ABOUT_MENU_ITEM:
			launchAbout();
			return true;
		default:
			return false;
		}
	}

	private void screenshotCalc() {
		mUserActivityTracker.reportUserAction(UserActionActivity.MAIN_ACTIVITY, UserActionEvent.SCREENSHOT);

		final Bitmap screenshot = mEmulatorFragment.getScreenshot();
		if (screenshot == null) {
			ErrorUtils.showErrorDialog(this, R.string.errorScreenshot);
			return;
		}
		final Bitmap scaledScreenshot = Bitmap.createScaledBitmap(screenshot, screenshot.getWidth() * 2,
				screenshot.getHeight() * 2, true);
		final File outputDir;
		final File outputFile;
		if (StorageUtils.hasExternalStorage()) {
			outputDir = new File(new File(StorageUtils.getPrimaryStoragePath(), "Wabbitemu"), "Screenshots");
			if (!outputDir.exists()) {
				outputDir.mkdirs();
			}

			final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
			final String now = sdf.format(new Date());

			final String fileName = "screenshot" + now.toString() + ".png";
			outputFile = new File(outputDir, fileName);
		} else {
			ErrorUtils.showErrorDialog(this, R.string.errorMissingSdCard);
			return;
		}

		try {
			final FileOutputStream out = new FileOutputStream(outputFile);
			scaledScreenshot.compress(Bitmap.CompressFormat.PNG, 100, out);
			out.close();
		} catch (final Exception e) {
			ErrorUtils.showErrorDialog(this, R.string.errorScreenshot);
			return;
		}

		final String formatString = getResources().getString(R.string.screenshotSuccess);
		final String successString = String.format(formatString, outputFile);
		final Toast toast = Toast.makeText(this, successString, Toast.LENGTH_LONG);
		toast.show();
	}

	private void launchAbout() {
		final Intent aboutIntent = new Intent(this, AboutActivity.class);
		startActivity(aboutIntent);
	}

	private void launchBrowse() {
		final Intent setupIntent = new Intent(this, BrowseActivity.class);
		// not perfect but it will work well enough
		final String extensions;
		switch (CalcInterface.GetModel()) {
		case CalcInterface.TI_73:
			extensions = "\\.(rom|sav|73[b|c|d|g|i|k|l|m|n|p|q|s|t|u|v|w|y|z])$";
			break;
		case CalcInterface.TI_82:
			extensions = "\\.(rom|sav|82[b|c|d|g|i|l|m|n|p|q|s|t|u|v|w|y|z])$";
			break;
		case CalcInterface.TI_83:
			extensions = "\\.(rom|sav|83[b|c|d|g|i|l|m|n|p|q|s|t|u|v|w|y|z])$";
			break;
		case CalcInterface.TI_83P:
		case CalcInterface.TI_83PSE:
		case CalcInterface.TI_84P:
		case CalcInterface.TI_84PSE:
			extensions = "\\.(rom|sav|8x[b|c|d|g|i|k|l|m|n|p|q|s|t|u|v|w|y|z])$";
			break;
		case CalcInterface.TI_84PCSE:
			extensions = "\\.(rom|sav|8[x|c][b|c|d|g|i|k|l|m|n|p|q|s|t|u|v|w|y|z])$";
			break;
		case CalcInterface.TI_85:
			extensions = "\\.(rom|sav|85[b|c|d|g|i|l|m|n|p|q|s|t|u|v|w|y|z])$";
			break;
		case CalcInterface.TI_86:
			extensions = "\\.(rom|sav|86[b|c|d|g|i|l|m|n|p|q|s|t|u|v|w|y|z])$";
			break;
		default:
			extensions = DEFAULT_FILE_REGEX;
			break;
		}
		final String description = getResources().getString(R.string.browseFileDescription);
		setupIntent.putExtra(IntentConstants.EXTENSION_EXTRA_REGEX, extensions);
		setupIntent.putExtra(IntentConstants.BROWSE_DESCRIPTION_EXTRA_STRING, description);
		startActivityForResult(setupIntent, LOAD_FILE_CODE);
	}

	private void launchWizard() {
		mWasUserLaunched = true;
		final Intent wizardIntent = new Intent(this, WizardActivity.class);
		startActivityForResult(wizardIntent, SETUP_WIZARD);
	}

	private void resetCalc() {
		mEmulatorFragment.resetCalc();
	}

	private void launchSettings() {
		final Intent intent = new Intent(this, SettingsActivity.class);
		startActivity(intent);
	}

	public void toggleHideyBar() {

		// The UI options currently enabled are represented by a bitfield.
		// getSystemUiVisibility() gives us that bitfield.
		final View decorView = getWindow().getDecorView();
		int uiOptions = decorView.getSystemUiVisibility();
		int newUiOptions = uiOptions;

		// Status bar hiding: Backwards compatible to Jellybean
		if (Build.VERSION.SDK_INT >= 16) {
			newUiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
		}

		// Immersive mode: Backward compatible to KitKat.
		// Note that this flag doesn't do anything by itself, it only augments
		// the behavior
		// of HIDE_NAVIGATION and FLAG_FULLSCREEN. For the purposes of this
		// sample
		// all three flags are being toggled together.
		// Note that there are two immersive mode UI flags, one of which is
		// referred to as "sticky".
		// Sticky immersive mode differs in that it makes the navigation and
		// status bars
		// semi-transparent, and the UI flag does not get cleared when the user
		// interacts with
		// the screen.
		if (Build.VERSION.SDK_INT >= 18) {
			newUiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
		}

		decorView.setSystemUiVisibility(newUiOptions);
	}
}