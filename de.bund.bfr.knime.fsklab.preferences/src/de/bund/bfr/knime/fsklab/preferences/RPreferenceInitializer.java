/***************************************************************************************************
 * Copyright (c) 2015 Federal Institute for Risk Assessment (BfR), Germany
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 * <p>
 * Contributors: Department Biological Safety - BfR
 **************************************************************************************************/
package de.bund.bfr.knime.fsklab.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

/**
 * Initializes preference page with default paths to R2 and R3 environments.
 *
 * @author Miguel Alba
 */
public class RPreferenceInitializer extends AbstractPreferenceInitializer {

	/** Path to R v.3 */
	static final String R3_PATH = "r3.path";

	private static RPreferenceProvider cachedProvider = null;

	@Override
	public void initializeDefaultPreferences() {

		String rHome = "";

		if (RPathUtil.getPackagedRHome() != null) {
			rHome = RPathUtil.getPackagedRHome().getAbsolutePath();
		} else if (RPathUtil.getSystemRHome() != null) {
			rHome = RPathUtil.getSystemRHome().getAbsolutePath();
		}

		IPreferenceStore store = Plugin.getDefault().getPreferenceStore();
		store.setDefault(R3_PATH, rHome);
	}

	/** @return provider to the path to the R3 executable. */
	public static final RPreferenceProvider getR3Provider() {
		final String r3Home = Plugin.getDefault().getPreferenceStore().getString(R3_PATH);
		if (cachedProvider == null || !cachedProvider.getRHome().equals(r3Home)) {
			cachedProvider = new DefaultRPreferenceProvider(r3Home);
		}

		return cachedProvider;
	}

	/**
	 * Invalidate the cached R3 preference provider returned by
	 * {@link #getR3Provider()}, to refetch R properties (which launches an external
	 * R command).
	 */
	public static final void invalidateProviderCache() {
		cachedProvider = null;
	}
}
