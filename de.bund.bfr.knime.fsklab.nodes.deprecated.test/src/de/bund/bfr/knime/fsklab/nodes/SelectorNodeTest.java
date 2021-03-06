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
package de.bund.bfr.knime.fsklab.nodes;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.sbml.jsbml.ASTNode;
import org.sbml.jsbml.AlgebraicRule;
import org.sbml.jsbml.Rule;

@SuppressWarnings("static-method")
public class SelectorNodeTest {

	@Test
	public void testCreatedSelectorNode() {
		List<Double> numbers = Arrays.asList(0.0, 1.1, 2.2);
		Rule r = new AlgebraicRule();

		ASTNode node = new SelectorNode(numbers, r).node;

		// Check root node: selector
		assertEquals(ASTNode.Type.FUNCTION_SELECTOR, node.getType());

		// Check second node: vector
		ASTNode vectorNode = node.getChild(0);
		assertEquals(ASTNode.Type.VECTOR, vectorNode.getType());

		// Check array
		ASTNode child0 = vectorNode.getChild(0);
		assertEquals(ASTNode.Type.REAL, child0.getType());
		assertEquals(0.0, child0.getReal(), .0);

		ASTNode child1 = vectorNode.getChild(1);
		assertEquals(ASTNode.Type.REAL, child0.getType());
		assertEquals(1.1, child1.getReal(), .0);

		ASTNode child2 = vectorNode.getChild(2);
		assertEquals(ASTNode.Type.REAL, child2.getType());
		assertEquals(2.2, child2.getReal(), .0);
	}

	@Test
	public void testGetArrayFromSelectorNode() {
		List<Double> numbers = Arrays.asList(0.0, 1.1, 2.2);
		Rule r = new AlgebraicRule();

		List<Double> obtainedArray = new SelectorNode(numbers, r).getArray();
		assertEquals(obtainedArray, numbers);
	}
}
