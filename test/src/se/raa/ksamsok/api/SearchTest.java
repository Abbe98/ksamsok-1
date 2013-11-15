package se.raa.ksamsok.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

import se.raa.ksamsok.api.exception.BadParameterException;
import se.raa.ksamsok.api.exception.DiagnosticException;
import se.raa.ksamsok.api.exception.MissingParameterException;
import se.raa.ksamsok.api.method.APIMethod;
import se.raa.ksamsok.api.method.APIMethod.Format;


public class SearchTest extends AbstractBaseTest{
	ByteArrayOutputStream out;
	int numberOfTotalHits;
	int numberOfHits;
	@Before
	public void setUp() throws MalformedURLException{
		super.setUp();
		reqParams = new HashMap<String,String>();
		reqParams.put("method", "search");
		reqParams.put("query","text=yxa");
		reqParams.put("fields","itemLabel,itemDescription,thumbnail,url");
	}

	@Test
	public void testSearchWithRecordSchemaXMLResponse(){
		reqParams.put("recordSchema","xml");
		try{
			// Assert the base search document structure
			NodeList recordList = assertBaseSearchDocument(Format.XML);
			for (int i = 0; i < recordList.getLength(); i++){
				Node record = recordList.item(i);
				NodeList fieldList = record.getChildNodes();
				assertTrue(fieldList.getLength()>1);
				// -1 because rel:score is the last node
				for(int j = 0; j < fieldList.getLength()-1;j++){
					Node field = fieldList.item(j);
					assertField(field);
				}
				Node relScore = record.getLastChild();
				assertRelScore(relScore);
			}
		} catch (Exception e){
			e.printStackTrace();
			fail(e.getMessage());
		}

	}

	@Test
	public void testSearchWithRecordSchemaPresentationResponse(){
		reqParams.put("recordSchema","presentation");
		try{
			// Assert the base search document structure
			NodeList recordList = assertBaseSearchDocument(Format.XML);
			for (int i = 0; i < recordList.getLength(); i++){
				assertEquals(2, recordList.item(i).getChildNodes().getLength());
				Node pres = recordList.item(i).getFirstChild();
				assertTrue(pres.getNodeName().equals("pres:item"));
				assertNotNull(pres.getFirstChild());
				//rel:score
				Node relScore = recordList.item(i).getLastChild();
				assertRelScore(relScore);
			}
		} catch (Exception e){
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testSearchWithRecordSchemaRDFResponse(){
		reqParams.put("recordSchema","rdf");
		try{
			NodeList recordList= assertBaseSearchDocument(Format.RDF);
			for (int i = 0; i < recordList.getLength(); i++){
				assertEquals(2, recordList.item(i).getChildNodes().getLength());
				//Try to creat an model from the embedded rdf
				Node rdf = recordList.item(i).getFirstChild();
				TransformerFactory transformerFactory = TransformerFactory.newInstance();
				Transformer	transform = transformerFactory.newTransformer();
				DOMSource source = new DOMSource(rdf);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				StreamResult strResult = new StreamResult(baos);
				transform.transform(source, strResult);
				Model m = ModelFactory.createDefaultModel();
				m.read(new ByteArrayInputStream(baos.toByteArray()),"");
				// Assert that the model is not empty
				assertFalse(m.isEmpty());
				//rel:score
				Node relScore = recordList.item(i).getLastChild();
				assertRelScore(relScore);
			}
		} catch (Exception e){
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	/**
	 * This method asserts the <field> tag
	 * @param node - The <field> node
	 */
	private void assertField(Node node) {
		assertTrue(node.getNodeName().equals("field"));
		assertEquals(1,node.getAttributes().getLength());
		assertTrue(node.getAttributes().item(0).getNodeName().equals("name"));
		String fieldName=node.getAttributes().item(0).getNodeValue();
		Node fieldValue = node.getFirstChild();
		switch (fieldName){
		case "itemId" :
		case "thumbnail" :
		case "url" :
			try {
				new URI(assertChild(fieldValue));
			} catch (URISyntaxException e) {
				fail("Non valid url: "+fieldValue);
			}
			break;
		case "itemLabel" :
		case "itemDescription" :
			assertChild(fieldValue);
			break;
		default :
			fail("Found a field value that should not be here: " +fieldName);
			break;
		}
	}

	/**
	 * This method makes the search request and assert the base document structure of the search result
	 * @param format
	 * @return
	 * @throws MissingParameterException
	 * @throws DiagnosticException
	 * @throws BadParameterException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	private NodeList assertBaseSearchDocument(Format format) throws MissingParameterException, DiagnosticException, BadParameterException, ParserConfigurationException, SAXException, IOException{
		out = new ByteArrayOutputStream();
		APIMethod search = apiMethodFactory.getAPIMethod(reqParams, out);
		search.setFormat(format);
		search.performMethod();
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder=null;
		docBuilder = docFactory.newDocumentBuilder();
		Document resultDoc = docBuilder.parse(new ByteArrayInputStream(out.toByteArray()));
		assertBaseDocProp(resultDoc);
		// Travel trough the document
		// Result, version and totalHits
		Node totalHits = assertResultAndVersion(resultDoc.getDocumentElement());
		assertParent(totalHits,"totalHits");
		// total hits value
		Node totalHitsValue = totalHits.getFirstChild();
		if (numberOfTotalHits>0){
			// Compare with the previous result if other test has been running
			assertEquals(numberOfTotalHits,Integer.parseInt(assertChild(totalHitsValue)));
		} else {
			numberOfTotalHits = Integer.parseInt(assertChild(totalHitsValue));
			assertTrue(numberOfTotalHits>1);
		}
		// Records
		Node records = totalHits.getNextSibling();
		assertParent(records,"records");
		// Echo 
		Node echo = records.getNextSibling();
		assertParent(echo,"echo");
		// StartRecord
		Node startRecord = echo.getFirstChild();
		assertParent(startRecord,"startRecord");
		// Start record value
		Node startRecordValue = startRecord.getFirstChild();
		assertEquals(1,Integer.parseInt(assertChild(startRecordValue)));
		// hitsPerPage
		Node hitsPerPage = startRecord.getNextSibling();
		assertParent(hitsPerPage,"hitsPerPage");
		// hitsPerPage value
		Node hitsPerPageValue = hitsPerPage.getFirstChild();
		if (numberOfHits>0){
			// Compare with the previous result if other test has been running
			assertEquals(numberOfHits, Integer.parseInt(assertChild(hitsPerPageValue)));
		} else {
			numberOfHits=Integer.parseInt(assertChild(hitsPerPageValue));
			assertTrue(numberOfHits>1);
		}
		// The record
		NodeList recordList = records.getChildNodes();
		assertEquals(numberOfHits,recordList.getLength());
		
		// query
		Node query = hitsPerPage.getNextSibling();
		assertParent(query, "query");
		assertTrue(echo.getLastChild().equals(query));
		// query value
		Node queryValue = query.getFirstChild();
		assertTrue(reqParams.get("query").equals(assertChild(queryValue)));
		
		return recordList;
	}
}
