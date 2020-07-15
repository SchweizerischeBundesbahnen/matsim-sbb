/* *********************************************************************** *
 * project: org.matsim.*
 * CountsLoadCurveGraph.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package ch.sbb.matsim.analysis.traveltimes;

import java.awt.Color;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.labels.StandardCategoryToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.DefaultCategoryDataset;
import org.matsim.counts.CountSimComparison;

public final class SBBCountsLoadCurveGraph {

	private final DefaultCategoryDataset dataset0;
	private final int linkCapacity;
	private final String title;
	private String linkId;
	private JFreeChart chart;

	public SBBCountsLoadCurveGraph(int linkCapacity, String title) {
		this.linkCapacity = linkCapacity;
		this.title = title;
		this.dataset0 = new DefaultCategoryDataset();
	}

	public void add2LoadCurveDataSets(final CountSimComparison cc) {
		String matsim_series = "Sim Volumes";
		String real_series = "Count Volumes";
		String h = Integer.toString(cc.getHour());
		this.dataset0.addValue(cc.getSimulationValue(), matsim_series, h);
		this.dataset0.addValue(cc.getCountValue(), real_series, h);

	}

	public JFreeChart createChart() {
		this.chart = ChartFactory.createBarChart(this.title, "Hour", "Volumes [veh/h]", this.dataset0,
				PlotOrientation.VERTICAL, true, // legend?
				true,
				false
		);

		CategoryPlot plot = this.chart.getCategoryPlot();

		final Marker marker = new ValueMarker(linkCapacity);
		marker.setPaint(Color.red);
		marker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
		marker.setLabelTextAnchor(TextAnchor.BOTTOM_RIGHT);
		marker.setLabel("Link Capacity");

		ValueAxis va = plot.getRangeAxis();
		va.setUpperBound(1.1 * linkCapacity);
		plot.addRangeMarker(marker);
		plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_45);

		BarRenderer renderer = new BarRenderer();
		renderer.setSeriesOutlinePaint(0, Color.black);
		renderer.setSeriesOutlinePaint(1, Color.black);
		renderer.setSeriesPaint(0, Color.getHSBColor((float) 0.0, (float) 1.0, (float) 0.9216));
		renderer.setSeriesPaint(1, Color.getHSBColor((float) 0.0, (float) 0.0, (float) 0.292));
		renderer.setSeriesToolTipGenerator(0, new StandardCategoryToolTipGenerator());
		renderer.setSeriesToolTipGenerator(1, new StandardCategoryToolTipGenerator());
		renderer.setItemMargin(0.0);

		renderer.setShadowVisible(false);
		renderer.setBarPainter(new StandardBarPainter());
		this.chart.setBackgroundPaint(Color.getHSBColor((float) 0.0, (float) 0.0, (float) 0.93));
		plot.setBackgroundPaint(Color.white);
		plot.setRangeGridlinePaint(Color.gray);
		plot.setRangeGridlinesVisible(true);

		plot.setRenderer(0, renderer);
		plot.setDomainAxisLocation(AxisLocation.BOTTOM_OR_RIGHT);
		plot.mapDatasetToRangeAxis(1, 1);

		final CategoryAxis axis1 = plot.getDomainAxis();
		axis1.setCategoryMargin(0.25); // leave a gap of 25% between categories

		return this.chart;
	}

	/**
	 * @return the linkId
	 */
	public String getLinkId() {
		return this.linkId;
	}

	/**
	 * @param linkId the linkId to set
	 */
	public void setLinkId(final String linkId) {
		this.linkId = linkId;
	}
}
