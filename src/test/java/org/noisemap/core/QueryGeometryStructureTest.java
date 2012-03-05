/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.noisemap.core;

import com.vividsolutions.jts.geom.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import junit.framework.TestCase;
import org.gdms.data.DataSource;
import org.gdms.data.DataSourceCreationException;
import org.gdms.data.DataSourceFactory;
import org.gdms.data.schema.MetadataUtilities;
import org.gdms.driver.DataSet;
import org.gdms.driver.DriverException;
import org.gdms.source.Source;
import org.junit.Before;

/**
 * Unit test & quick benchmark of implemented GeometryStructure
 */
public class QueryGeometryStructureTest extends TestCase {
    private DataSourceFactory dsf;
    
    @Before@Override
    public void setUp() throws Exception {
            //Init datasource folder
            String targetPath="target"+File.separatorChar;
            File targetDir = new File(targetPath);
            File sourceDir = new File(targetPath+"sources"+File.separatorChar);
            dsf = new DataSourceFactory(sourceDir.getAbsolutePath(),
                    targetDir.getAbsolutePath());
    }
    /**
     * This function does not assert,
     * but keep track of the evolution of geometry structures optimisations
     */
    public void testBenchQueryGeometryStructure() throws DataSourceCreationException, DriverException {
        
        System.out.println("________________________________________________");
        System.out.println("QueryGeometryStructure Bench :");

        //Register gdms file
        File sourcesGdmsFile = new File("src"+File.separatorChar+
                "test"+File.separatorChar+
                "resource"+File.separatorChar+
                "org"+File.separatorChar+
                "noisemap"+File.separatorChar+
                "core"+File.separatorChar+
                "multiple_lines.gdms"
                );
        dsf.getSourceManager().register("soundSources", 
                sourcesGdmsFile);
        DataSource sdsSources = dsf.getDataSource(sourcesGdmsFile);
        sdsSources.open();
        int spatialSourceFieldIndex = MetadataUtilities.getSpatialFieldIndex(sdsSources.getMetadata());
        long rowCount = sdsSources.getRowCount();
        
        
        //Init quadtree structures
        long startFeedQuadree=System.currentTimeMillis();
        QueryQuadTree quadIndex = new QueryQuadTree();        
        for (Integer rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            Geometry sourceGeom = sdsSources.getFieldValue(rowIndex, spatialSourceFieldIndex).getAsGeometry();
            quadIndex.appendGeometry(sourceGeom, rowIndex);
        }
        long feedQuadtreeTime = System.currentTimeMillis() - startFeedQuadree;
        //Init grid structure
        long startFeedGrid=System.currentTimeMillis();
        QueryGridIndex gridIndex = new QueryGridIndex(sdsSources.getFullExtent(),8,8);
        for (Integer rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            Geometry sourceGeom = sdsSources.getFieldValue(rowIndex, spatialSourceFieldIndex).getAsGeometry();
            gridIndex.appendGeometry(sourceGeom, rowIndex);
        }
        long feedGridTime = System.currentTimeMillis() - startFeedGrid;
        System.out.println("Feed structures with "+rowCount+" items..");
        System.out.println("Feed QueryQuadTree in "+feedQuadtreeTime+" ms");
        System.out.println("Feed QueryGridIndex in "+feedGridTime+" ms");
        
        Envelope envScene = sdsSources.getFullExtent();
	
        
        
        Envelope testExtract = new Envelope(envScene.centre());
        testExtract.expandBy(envScene.getWidth()/3., envScene.getHeight()/3);
        
        //Request for each implementation of QueryGeometryStructure
        long debQuery = System.nanoTime();
        long nbItemsReturned = countResult(quadIndex.query(testExtract));
        System.out.println("QueryQuadTree query time in "+(System.nanoTime() - debQuery)/1e6+" ms with "+nbItemsReturned+" items returned.");
        debQuery = System.nanoTime();
        nbItemsReturned = countResult(gridIndex.query(testExtract));
        System.out.println("QueryGridIndex query time in "+(System.nanoTime() - debQuery)/1e6+" ms with "+nbItemsReturned+" items returned.");
    }
    private Integer countResult(Iterator<Integer> result) {
        Integer counter=0;
        while(result.hasNext()) {
            result.next();
            counter++;
        }        
        return counter;
    }
    /**
     * queryResult must contains all items of expectedResult
     * @param expectedResult
     * @param queryResult 
     */
    private void queryAssert(List<Integer> expectedResult,Iterator<Integer> queryResult) {
        List<Integer> remainingExpectedResult = new ArrayList<Integer>();
        remainingExpectedResult.addAll(expectedResult);
        while(queryResult.hasNext()) {
            Integer geoIndex = queryResult.next();
            if(remainingExpectedResult.contains(geoIndex)) {
                remainingExpectedResult.remove(geoIndex);
            }
        }
        assertTrue("QueryGeometryStructure does not return the expected row index",remainingExpectedResult.isEmpty());
    }
    /**
     * Add lines in a geometry query structure
     */
    public void testLineSegmentsIntersect() {
        GeometryFactory fact = new GeometryFactory();
        //Init segments
        LineSegment[] segments= new LineSegment[] {
            new LineSegment(new Coordinate(2,1),new Coordinate(7,3)),  //A
            new LineSegment(new Coordinate(8,3),new Coordinate(10,1)), //B
            new LineSegment(new Coordinate(2,6),new Coordinate(7,6))   //C
        };
        //Init scene envelope
        Envelope envTest = new Envelope(new Coordinate(0,0),new Coordinate(11,11));
        
        //Feed grid structure
        QueryGridIndex gridIndex = new QueryGridIndex(envTest,4,4);
        for(Integer idseg = 0;idseg < segments.length ; idseg++) {
            gridIndex.appendGeometry(segments[idseg].toGeometry(fact),idseg);
        }
        
        //Feed JTS Quadtree structure
        QueryQuadTree quadIndex = new QueryQuadTree();
        for(Integer idseg = 0;idseg < segments.length ; idseg++) {
            quadIndex.appendGeometry(segments[idseg].toGeometry(fact),idseg);
        }
        
        
        //Intersection test
        List<Integer> expectedIndex;
        expectedIndex=new ArrayList<Integer>();
        expectedIndex.add(0);
        expectedIndex.add(1);
        Envelope queryEnv = new Envelope(new Coordinate(7,2),new Coordinate(8,3));
        queryAssert(expectedIndex, quadIndex.query(queryEnv));
        queryAssert(expectedIndex, gridIndex.query(queryEnv));
        
        //Other one        
        expectedIndex.add(2);
        queryEnv = new Envelope(new Coordinate(7,2),new Coordinate(8,6));
        queryAssert(expectedIndex, quadIndex.query(queryEnv));
        queryAssert(expectedIndex, gridIndex.query(queryEnv));
    }
}