/*******************************************************************************
 * Copyright (c) 2015 Federal Institute for Risk Assessment (BfR), Germany
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Department Biological Safety - BfR
 *******************************************************************************/
package de.bund.bfr.knime.pmm.common.ui;

import java.awt.Color;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class DoubleTextField extends JTextField implements DocumentListener,
		FocusListener {

	private static final long serialVersionUID = 1L;

	private double minValue;
	private double maxValue;
	private boolean optional;

	private boolean isValueValid;
	private Double value;

	private List<TextListener> listeners;

	public DoubleTextField() {
		this(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false);
	}

	/*
	 * @param optional defines whether the value can also be left empty.
	 */
	public DoubleTextField(boolean optional) {
		this(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, optional);
	}

	public DoubleTextField(double minValue, double maxValue) {
		this(minValue, maxValue, false);
	}

	public DoubleTextField(double minValue, double maxValue, boolean optional) {
		this.minValue = minValue;
		this.maxValue = maxValue;
		this.optional = optional;
		getDocument().addDocumentListener(this);
		addFocusListener(this);
		listeners = new ArrayList<>();
		textChanged();
		formatText();
	}

	public void addTextListener(TextListener listener) {
		listeners.add(listener);
	}

	public void removeTextListener(TextListener listener) {
		listeners.remove(listener);
	}

	public boolean isValueValid() {
		return isValueValid;
	}

	public Double getValue() {
		return value;
	}

	public void setValue(Double value) {
		if (value != null) {
			setText(value.toString());
		} else {
			setText("");
		}

		formatText();
		setCaretPosition(0);
	}

	@Override
	public void insertUpdate(DocumentEvent e) {
		textChanged();
	}

	@Override
	public void removeUpdate(DocumentEvent e) {
		textChanged();
	}

	@Override
	public void changedUpdate(DocumentEvent e) {
		textChanged();
	}

	@Override
	public void focusGained(FocusEvent e) {
		selectAll();
	}

	@Override
	public void focusLost(FocusEvent e) {
		formatText();
	}

	@Override
	public Color getForeground() {
		if (!isValueValid && isEnabled()) {
			return Color.RED;
		} else {
			return super.getForeground();
		}
	}

	@Override
	public Color getBackground() {
		if (!isValueValid && isEnabled() && getDocument() != null
				&& getText().trim().isEmpty()) {
			return Color.RED;
		} else {
			return super.getBackground();
		}
	}

	protected void formatText() {
		if (value != null) {
			setText(getText().replace(",", "."));
		}
	}

	private void textChanged() {
		if (getText().trim().isEmpty()) {
			value = null;

			if (optional) {
				isValueValid = true;
			} else {
				isValueValid = false;
			}
		} else {
			try {
				value = Double.parseDouble(getText().replace(",", "."));

				if (value >= minValue && value <= maxValue) {
					isValueValid = true;
				} else {
					isValueValid = false;
				}
			} catch (NumberFormatException e) {
				value = null;
				isValueValid = false;
			}
		}

		for (TextListener listener : listeners) {
			listener.textChanged(this);
		}
	}

}
