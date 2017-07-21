/*
 ***************************************************************************************************
 * Copyright (c) 2017 Federal Institute for Risk Assessment (BfR), Germany
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 *
 * Contributors: Department Biological Safety - BfR
 *************************************************************************************************
 */
package de.bund.bfr.knime.fsklab.nodes.rakip.runner;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentColorChooser;
import org.knime.core.node.defaultnodesettings.DialogComponentNumberEdit;
import org.knime.core.node.defaultnodesettings.DialogComponentString;

public class NodeFactory extends org.knime.core.node.NodeFactory<NodeModel> {

	@Override
	public NodeModel createNodeModel() {
		return new NodeModel();
	}

	@Override
	protected int getNrNodeViews() {
		return 1;
	}

	@Override
	public org.knime.core.node.NodeView<NodeModel> createNodeView(int viewIndex, NodeModel nodeModel) {
		return new NodeView(nodeModel);
	}

	@Override
	protected boolean hasDialog() {
		return true;
	}

	@Override
	protected org.knime.core.node.NodeDialogPane createNodeDialogPane() {
		// return new DefaultNodeSettingsPane();
		DefaultNodeSettingsPane pane = new DefaultNodeSettingsPane();
		pane.createNewGroup("Options");

		NodeSettings settings = new NodeSettings();

		// Width component
		DialogComponentNumberEdit widthComp = new DialogComponentNumberEdit(settings.widthModel, "Width");
		widthComp.setToolTipText("Width of the plot");
		pane.addDialogComponent(widthComp);

		// Height component
		DialogComponentNumberEdit heightComp = new DialogComponentNumberEdit(settings.heightModel, "Height");
		heightComp.setToolTipText("Height of the plot");
		pane.addDialogComponent(heightComp);

		// Resolution component
		DialogComponentString resolutionComp = new DialogComponentString(settings.resolutionModel, "Resolution");
		resolutionComp.setToolTipText(
				"Nominal resolution in ppi which will be recorded in the bitmap file, if a positive integer.");
		pane.addDialogComponent(resolutionComp);

		// Background colour component
		DialogComponentColorChooser colorComp = new DialogComponentColorChooser(settings.colourModel,
				"Background colour", true);
		colorComp.setToolTipText("Background colour");
		pane.addDialogComponent(colorComp);

		// Text point size component
		DialogComponentNumberEdit textPointSizeComp = new DialogComponentNumberEdit(settings.textPointSizeModel,
				"Text point size");
		textPointSizeComp.setToolTipText("Point size of plotted text, interpreted as big points (1/72 inch) at res ppi.");

		pane.addDialogComponent(textPointSizeComp);

		return pane;
	}
}