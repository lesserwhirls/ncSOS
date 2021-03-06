/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asascience.ncsos.cdmclasses;

import com.asascience.ncsos.go.ObservationOffering;
import com.asascience.ncsos.service.BaseRequestHandler;
import com.asascience.ncsos.util.DatasetHandlerAdapter;

import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.w3c.dom.Document;

import ucar.nc2.Variable;
import ucar.nc2.ft.PointFeature;
import ucar.nc2.ft.PointFeatureIterator;
import ucar.nc2.ft.StationTimeSeriesFeature;
import ucar.nc2.ft.StationTimeSeriesFeatureCollection;
import ucar.nc2.units.DateFormatter;
import ucar.nc2.units.DateRange;
import ucar.nc2.units.DateUnit;
import ucar.unidata.geoloc.Station;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides methods to gather information from TimeSeries datasets needed for requests: GetCapabilities, GetObservations
 * @author abird
 * @version 1.0.0
 */
public class TimeSeries extends baseCDMClass implements iStationData {

    private StationTimeSeriesFeatureCollection tsData;
    private List<Station> tsStationList;
    private final ArrayList<String> eventTimes;
    private final String[] variableNames;

    /**
     * 
     * @param stationName
     * @param eventTime
     * @param variableNames
     */
    public TimeSeries(String[] stationName, String[] eventTime, String[] variableNames) {
        startDate = null;
        endDate = null;
        this.variableNames = variableNames;
        this.reqStationNames = new ArrayList<String>();
        reqStationNames.addAll(Arrays.asList(stationName));
        if (eventTime != null) {
            this.eventTimes = new ArrayList<String>();
            this.eventTimes.addAll(Arrays.asList(eventTime));
        }
        else
            this.eventTimes = null;
    }

    /*******************TIMSERIES*************************/
    private String createTimeSeriesData(int stNum) throws IOException {
        //create the iterator for the feature
        PointFeatureIterator iterator = tsData.getStationFeature(tsStationList.get(stNum)).getPointFeatureIterator(-1);

        //create the string builders and other things needed
        StringBuilder builder = new StringBuilder();
        DateFormatter dateFormatter = new DateFormatter();
        List<String> valueList = new ArrayList<String>();

        while (iterator.hasNext()) {
            PointFeature pointFeature = iterator.next();

            //if no event time
            if (eventTimes == null) {
                createTimeSeriesData(valueList, dateFormatter, pointFeature, builder, stNum);
                //count = (stationTimeSeriesFeature.size());
            } //if bounded event time        
            else if (eventTimes.size() > 1) {
                parseMultiTimeEventTimeSeries(df, chrono, pointFeature, valueList, dateFormatter, builder, stNum);
            } //if single event time        
            else {
                if (eventTimes.get(0).contentEquals(dateFormatter.toDateTimeStringISO(
                		new Date(pointFeature.getObservationTimeAsCalendarDate().getMillis())))){
                    createTimeSeriesData(valueList, dateFormatter, pointFeature, builder, stNum);
                }
            }
        }
        iterator.finish();
        //setCount(count);
        return builder.toString();
    }


    private void createTimeSeriesData(List<String> valueList, DateFormatter dateFormatter, 
    								  PointFeature pointFeature, StringBuilder builder, int stNum) {
        //count++;
        valueList.clear();
        
        Date valDate = new Date(pointFeature.getObservationTimeAsCalendarDate().getMillis());
        valueList.add("time=" + dateFormatter.toDateTimeStringISO(valDate));
        valueList.add(STATION_STR + stNum);
        try {
            for (String variableName : variableNames) {
                valueList.add(variableName + "=" + pointFeature.getData().getScalarObject(variableName).toString());
            }
        } catch (Exception ex) {
            // couldn't find a data var
            builder.delete(0, builder.length());
            builder.append("ERROR =reading data from dataset: ").append(ex.getLocalizedMessage()).append(
            			". Most likely this property does not exist or is improperly stored in the dataset.");
            return;
        }

        for (int i = 0; i < valueList.size(); i++) {
            builder.append(valueList.get(i));
            if (i < valueList.size() - 1) {
                builder.append(",");
            }
        }
        try {
            //builder.append(tokenJoiner.join(valueList));
            // TODO:  conditional inside loop...
            if (tsData.getStationFeature(tsStationList.get(stNum)).size() > 1) {
                builder.append(";");
            }
        } catch (Exception ex) {
            builder = new StringBuilder();
            builder.append("ERROR=received the following error when reading the data of the dataset: ").append(ex.getLocalizedMessage());
        }
    }

    /**
     * 
     * @param df
     * @param chrono
     * @param pointFeature
     * @param valueList
     * @param dateFormatter
     * @param builder
     * @param stNum
     * @throws IOException
     */
    public void parseMultiTimeEventTimeSeries(DateFormatter df, Chronology chrono, PointFeature pointFeature, 
    										  List<String> valueList, DateFormatter dateFormatter, 
    										  StringBuilder builder, int stNum) throws IOException {
        //get start/end date based on iso date format date        

        DateTime dtStart = new DateTime(df.getISODate(eventTimes.get(0)), chrono);
        DateTime dtEnd = new DateTime(df.getISODate(eventTimes.get(1)), chrono);
        DateTime tsDt = new DateTime(new Date(pointFeature.getObservationTimeAsCalendarDate().getMillis()));
     
        //find out if current time(searchtime) is one or after startTime
        //same as start
        if (tsDt.isEqual(dtStart)) {
            createTimeSeriesData(valueList, dateFormatter, pointFeature, builder, stNum);
        } //equal end
        else if (tsDt.isEqual(dtEnd)) {
            createTimeSeriesData(valueList, dateFormatter, pointFeature, builder, stNum);
        } //afterStart and before end       
        else if (tsDt.isAfter(dtStart) && (tsDt.isBefore(dtEnd))) {
            createTimeSeriesData(valueList, dateFormatter, pointFeature, builder, stNum);
        }
    }

    @Override
    public void setInitialLatLonBoundaries(List<Station> tsStationList) {
        upperLat = tsStationList.get(0).getLatitude();
        lowerLat = tsStationList.get(0).getLatitude();
        upperLon = tsStationList.get(0).getLongitude();
        lowerLon = tsStationList.get(0).getLongitude();
        upperAlt = tsStationList.get(0).getAltitude();
        lowerAlt = tsStationList.get(0).getAltitude();
    }

    @Override
    public void setData(Object featureCollection) throws IOException {
        try {
            this.tsData = (StationTimeSeriesFeatureCollection) featureCollection;
            String genericName = this.tsData.getCollectionFeatureType().name()+"-";
            // Try to get stations by name, both with URN procedure and without
            tsStationList = tsData.getStations(reqStationNames);
            
            
            // based on files with cf_role 
            for (String s : reqStationNames) {
                String[] urns = s.split(":");
                String statUrn = urns[urns.length - 1];
                Station st = tsData.getStation(statUrn);
                if (st != null) {
                    tsStationList.add(st);
                }
                else if (statUrn.startsWith(genericName)){
                	// check to see if generic name (ie: STATION-0)
                	try {
                		Integer sIndex = Integer.valueOf(statUrn.substring(genericName.length()));
                		st = tsData.getStations().get(sIndex);
                		if(st != null){
                			tsStationList.add(st);
                		}
                	}
                	catch(Exception n){
                		n.printStackTrace();
                	}
                }
            }

            setNumberOfStations(tsStationList.size());

            if (tsStationList.size() > 0) {
                DateTime dtStart = null;
                DateTime dtEnd = null;
                DateTime dtStartt = null;
                DateTime dtEndt = null;
                DateRange dateRange = null;
                for (int i = 0; i < tsStationList.size(); i++) {
                    //set it on the first one
                    //calc bounds in loop
                    DatasetHandlerAdapter.calcBounds(tsData.getStationFeature(tsStationList.get(i)));
                    if (i == 0) {
                        setInitialLatLonBoundaries(tsStationList);

                        dateRange = tsData.getStationFeature(tsStationList.get(0)).getDateRange();
                        dtStart = new DateTime(dateRange.getStart().getDate(), chrono);
                        dtEnd = new DateTime(dateRange.getEnd().getDate(), chrono);
                    } else {
                        dateRange = tsData.getStationFeature(tsStationList.get(i)).getDateRange();
                        dtStartt = new DateTime(dateRange.getStart().getDate(), chrono);
                        dtEndt = new DateTime(dateRange.getEnd().getDate(), chrono);
                        if (dtStartt.isBefore(dtStart)) {
                            dtStart = dtStartt;
                        }
                        if (dtEndt.isAfter(dtEnd)) {
                            dtEnd = dtEndt;
                        }
                        checkLatLonAltBoundaries(tsStationList, i);
                    }
                }
                setStartDate(df.toDateTimeStringISO(dtStart.toDate()));
                setEndDate(df.toDateTimeStringISO(dtEnd.toDate()));
            }
            
        } catch (Exception ex) {
            _log.error("TimeSeries - setData; exception:\n" + ex.toString());
            throw new IOException(ex.toString());
        }
    }

    @Override
    public String getDataResponse(int stNum) {
        try {
            if (tsData != null) {
                return createTimeSeriesData(stNum);
            }
        } catch (IOException ex) {
            Logger.getLogger(TimeSeries.class.getName()).log(Level.SEVERE, null, ex);
            return DATA_RESPONSE_ERROR + TimeSeries.class;
        }
        return DATA_RESPONSE_ERROR + TimeSeries.class;
    }

    @Override
    public String getStationName(int idNum) {
        if (tsData != null && getNumberOfStations() > idNum) {
        	String statName = tsStationList.get(idNum).getName();
        	if (statName.isEmpty()){
        		// return generic
        		statName = this.tsData.getCollectionFeatureType().name()+"-"+idNum;
        	}
            return statName;
        } else {
            return Invalid_Station;
        }
    }

    @Override
    public double getLowerLat(int stNum) {
        if (tsData != null) {
            return (tsStationList.get(stNum).getLatitude());
        } else {
            return Invalid_Value;
        }
    }

    @Override
    public double getLowerLon(int stNum) {
        if (tsData != null) {
            return (tsStationList.get(stNum).getLongitude());
        } else {
            return Invalid_Value;
        }
    }

    @Override
    public double getUpperLat(int stNum) {
        if (tsData != null) {
            return (tsStationList.get(stNum).getLatitude());
        } else {
            return Invalid_Value;
        }
    }

    @Override
    public double getUpperLon(int stNum) {
        if (tsData != null) {
            return (tsStationList.get(stNum).getLongitude());
        } else {
            return Invalid_Value;
        }
    }
    
    @Override
    public double getLowerAltitude(int stNum) {
        if (tsData != null) {
            double retval = tsStationList.get(stNum).getAltitude();
            if (Double.toString(retval).equalsIgnoreCase("nan"))
                retval = 0.0;
            return retval;
        } else {
            return Invalid_Value;
        }
    }
    
    @Override
    public double getUpperAltitude(int stNum) {
        if (tsData != null) {
            double retval = tsStationList.get(stNum).getAltitude();
            if (Double.toString(retval).equalsIgnoreCase("nan"))
                retval = 0.0;
            return retval;
        } else {
            return Invalid_Value;
        }
    }

    @Override
    public String getTimeEnd(int stNum) {
        try {
            if (tsData != null) {
                DatasetHandlerAdapter.calcBounds(tsData.getStationFeature(tsStationList.get(stNum)));
                DateRange dateRange = tsData.getStationFeature(tsStationList.get(stNum)).getDateRange();
                DateTime dtEnd = new DateTime(dateRange.getEnd().getDate(), chrono);
                return (df.toDateTimeStringISO(dtEnd.toDate()));
            }
        } catch (IOException ex) {
            Logger.getLogger(TimeSeries.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ERROR_NULL_DATE;
    }

    @Override
    public String getTimeBegin(int stNum) {
        try {
            if (tsData != null) {
                DatasetHandlerAdapter.calcBounds(tsData.getStationFeature(tsStationList.get(stNum)));
                DateRange dateRange = tsData.getStationFeature(tsStationList.get(stNum)).getDateRange();
                DateTime dtStart = new DateTime(dateRange.getStart().getDate(), chrono);
                return (df.toDateTimeStringISO(dtStart.toDate()));
            }
        } catch (IOException ex) {
            Logger.getLogger(TimeSeries.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ERROR_NULL_DATE;
    }

    @Override
    public String getDescription(int stNum) {
       return "descrip";
    }

    public List<String> getLocationsString(int stNum) {
        List<String> retval = new ArrayList<String>();
        retval.add(this.getLowerLat(stNum) + " " + this.getLowerLon(stNum));
        return retval;
    }
}
