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
package de.bund.bfr.knime.pmm.extendedtable;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.DOMOutputter;

import de.bund.bfr.knime.pmm.extendedtable.items.AgentXml;
import de.bund.bfr.knime.pmm.extendedtable.items.LiteratureItem;
import de.bund.bfr.knime.pmm.extendedtable.items.LiteratureItem.Type;
import de.bund.bfr.knime.pmm.extendedtable.items.MatrixXml;

public class Model1Metadata {

	private static final String ELEMENT_PMMDOC = "PmmDoc";

	private AgentXml agentXml;
	private MatrixXml matrixXml;
	private List<LiteratureItem> modelLiteratureItems;
	private List<LiteratureItem> estimatedModelLiteratureItems;
	private String warning;

	public Model1Metadata() {
		agentXml = null;
		matrixXml = null;
		modelLiteratureItems = new ArrayList<>();
		estimatedModelLiteratureItems = new ArrayList<>();
		warning = "";
	}

	public Model1Metadata(String xmlString) throws IOException, JDOMException {
		this();
		SAXBuilder builder = new SAXBuilder();
		Document doc = builder.build(new StringReader(xmlString));

		Element rootElement = doc.getRootElement();
		parseElement(rootElement);
	}

	private void parseElement(Element rootElement) {

		Element agentElement = rootElement.getChild("model1Agent");
		if (agentElement != null) {
			agentXml = new AgentXml(agentElement);
		}

		Element matrixElement = rootElement.getChild("model1Matrix");
		if (matrixElement != null) {
			matrixXml = new MatrixXml(matrixElement);
		}

		rootElement.getChildren("MLiteratureItem").stream().map(LiteratureItem::new).forEach(modelLiteratureItems::add);
		rootElement.getChildren("EstimatedModelLiterature").stream().map(LiteratureItem::new).forEach(estimatedModelLiteratureItems::add);
	}

	public void addWarning(String warning) {
		this.warning += warning;
	}

	public String getWarning() {
		return warning;
	}

	public void setAgentXml(AgentXml agentXml) {
		this.agentXml = agentXml;
	}

	public void clearAgentXml() {
		this.agentXml = null;
	}

	public void setMatrixXml(MatrixXml matrixXml) {
		this.matrixXml = matrixXml;
	}

	public void clearMatrixXml() {
		this.matrixXml = null;
	}
	
	public void addLiteratureItem(LiteratureItem literatureItem) {
		if (literatureItem.litType == Type.M) {
			modelLiteratureItems.add(literatureItem);
		} else if (literatureItem.litType == Type.EM) {
			estimatedModelLiteratureItems.add(literatureItem);
		}
	}

	public void removeLiteratureItem(LiteratureItem literatureItem) {
		if (literatureItem.litType == Type.M) {
			modelLiteratureItems.remove(literatureItem);
		} else if (literatureItem.litType == Type.EM) {
			estimatedModelLiteratureItems.remove(literatureItem);
		}
	}

	public org.w3c.dom.Document getW3C() {
		try {
			Document doc = toXmlDocument();
			return new DOMOutputter().output(doc);
		} catch (JDOMException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public Document toXmlDocument() {
		Document doc = new Document();
		Element rootElement = new Element(ELEMENT_PMMDOC);
		doc.setRootElement(rootElement);

		if (agentXml != null) {
			rootElement.addContent(agentXml.toXmlElement());
		}
		if (matrixXml != null) {
			rootElement.addContent(matrixXml.toXmlElement());
		}
		for (LiteratureItem literatureItem : modelLiteratureItems) {
			rootElement.addContent(literatureItem.toXmlElement());
		}
		modelLiteratureItems.stream().map(LiteratureItem::toXmlElement).forEach(rootElement::addContent);
		estimatedModelLiteratureItems.stream().map(LiteratureItem::toXmlElement).forEach(rootElement::addContent);

		return doc;
	}
}
