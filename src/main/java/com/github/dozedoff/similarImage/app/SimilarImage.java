/*  Copyright (C) 2013  Nicholas Wright
    
    This file is part of similarImage - A similar image finder using pHash
    
    mmut is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.dozedoff.similarImage.app;

import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JProgressBar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dozedoff.commonj.file.FilenameFilterVisitor;
import com.github.dozedoff.commonj.filefilter.SimpleImageFilter;
import com.github.dozedoff.commonj.time.StopWatch;
import com.github.dozedoff.similarImage.db.DBWriter;
import com.github.dozedoff.similarImage.db.FilterRecord;
import com.github.dozedoff.similarImage.db.ImageRecord;
import com.github.dozedoff.similarImage.db.Persistence;
import com.github.dozedoff.similarImage.duplicate.DuplicateEntry;
import com.github.dozedoff.similarImage.duplicate.ImageInfo;
import com.github.dozedoff.similarImage.duplicate.SortSimilar;
import com.github.dozedoff.similarImage.gui.DisplayGroup;
import com.github.dozedoff.similarImage.gui.SimilarImageGUI;
import com.github.dozedoff.similarImage.hash.PhashWorker;
import com.github.dozedoff.similarImage.io.ImageProducer;
import com.j256.ormlite.dao.CloseableWrappedIterable;

public class SimilarImage {
	private final static Logger logger = LoggerFactory.getLogger(SimilarImage.class);

	private final int WORKER_THREADS = 6;
	private final int LOADER_THREADS = 2;
	private final int LOADER_PRIORITY = 2;

	private final int THUMBNAIL_DIMENSION = 500;
	private final int PRODUCER_QUEUE_SIZE = 400;

	SimilarImageGUI gui;
	DisplayGroup displayGroup;

	private ImageProducer producer;
	private PhashWorker workers[] = new PhashWorker[WORKER_THREADS];
	private Persistence persistence;
	private SortSimilar sorter;
	private DBWriter dbWriter;

	public static void main(String[] args) {
		new SimilarImage().init();
	}

	public void init() {
		persistence = new Persistence();
		sorter = new SortSimilar(persistence);
		dbWriter = new DBWriter(persistence);

		producer = new ImageProducer(PRODUCER_QUEUE_SIZE, persistence);
		producer.setThreadPriority(LOADER_PRIORITY);
		producer.startLoader(LOADER_THREADS);

		gui = new SimilarImageGUI(this, persistence);
		displayGroup = new DisplayGroup();
	}

	public JProgressBar getBufferLevel() {
		return producer.getBufferLevel();
	}

	public void indexImages(String path) {
		Thread t = new ImageIndexer(path);
		t.start();
	}

	public void sortDuplicates(int hammingDistance) {
		Thread t = new ImageSorter(hammingDistance);
		t.start();
	}

	public void sortFilter(int hammingDistance, String reason) {
		Thread t = new FilterSorter(hammingDistance, reason);
		t.start();
	}

	private void findImages(String path, LinkedList<Path> imagePaths) {
		FilenameFilterVisitor visitor = new FilenameFilterVisitor(imagePaths, new SimpleImageFilter());
		Path directoryToSearch = Paths.get(path);
		try {
			Files.walkFileTree(directoryToSearch, visitor);
		} catch (IOException e) {
			logger.error("Failed to walk file tree", e);
			return;
		}

		logger.info("Found {} images", imagePaths.size());
		gui.setTotalFiles(imagePaths.size());
	}

	private void calculateHashes(List<Path> imagePaths) {
		StopWatch sw = new StopWatch();

		sw.start();
		logger.info("Creating and starting workers...");
		for (int i = 0; i < WORKER_THREADS; i++) {
			workers[i] = new PhashWorker(producer, dbWriter);
			workers[i].start();
		}

		logger.info("Adding paths to ImageProducer");
		producer.addToLoad(imagePaths);

		for (int i = 0; i < WORKER_THREADS; i++) {
			try {
				workers[i].join();
			} catch (InterruptedException e) {
				logger.info("Interrupted waiting for {}", workers[i].getName());
			}
		}

		sw.stop();
		logger.info("Took {} to process {} images", sw.getTime(), imagePaths.size());
	}

	public void stopWorkers() {
		logger.info("Stopping all workers...");
		producer.clear();
		for (PhashWorker phw : workers) {
			if (phw != null) {
				logger.info("Stopping {}...", phw.getName());
				phw.stopWorker();
			}
		}
	}

	public void displayGroup(long group) {
		Set<ImageRecord> grouplist = sorter.getGroup(group);
		LinkedList<JComponent> images = new LinkedList<JComponent>();
		Dimension imageDim = new Dimension(THUMBNAIL_DIMENSION, THUMBNAIL_DIMENSION);

		logger.info("Loading {} thumbnails for group {}", grouplist.size(), group);

		for (ImageRecord rec : grouplist) {
			Path path = Paths.get(rec.getPath());
			ImageInfo info = new ImageInfo(path, persistence);
			DuplicateEntry entry = new DuplicateEntry(this, info, persistence, imageDim);
			images.add(entry);
		}

		displayGroup.displayImages(group, images);
	}

	public void ignoreImage(ImageRecord toIgnore) {
		sorter.ignore(toIgnore);
	}

	class ImageIndexer extends Thread {
		String path;

		public ImageIndexer(String path) {
			this.path = path;
		}

		@Override
		public void run() {
			producer.clear();
			gui.setStatus("Running...");
			logger.info("Hashing images in {}", path);
			LinkedList<Path> imagePaths = new LinkedList<Path>();

			gui.setStatus("Looking for images...");
			findImages(path, imagePaths);
			gui.setStatus("Hashing images...");
			calculateHashes(imagePaths);
			gui.setStatus("Done");
		}
	}

	class ImageSorter extends Thread {
		int hammingDistance = 0;

		public ImageSorter(int hammingDistance) {
			super();
			this.hammingDistance = hammingDistance;
		}

		@Override
		public void run() {
			List<ImageRecord> dBrecords = new LinkedList<ImageRecord>();

			sorter.clear();
			gui.setStatus("Sorting...");

			try {
				dBrecords = persistence.getAllRecords();
			} catch (SQLException e) {
				logger.warn("Failed to load records - {}", e.getMessage());
			}

			if (hammingDistance == 0) {
				CloseableWrappedIterable<ImageRecord> records = persistence.getImageRecordIterator();
				sorter.sortExactMatch(records);
			} else {
				sorter.sortHammingDistance(hammingDistance, dBrecords);
			}
			gui.setStatus("" + sorter.getNumberOfDuplicateGroups() + " Groups");
			List<Long> groups = sorter.getDuplicateGroups();
			gui.populateGroupList(groups);
		}
	}

	class FilterSorter extends Thread {
		int hammingDistance = 0;
		String reason;
		List<ImageRecord> dBrecords = new LinkedList<ImageRecord>();
		List<FilterRecord> filterRecords = new LinkedList<FilterRecord>();

		public FilterSorter(int hammingDistance, String reason) {
			super();
			this.hammingDistance = hammingDistance;
			this.reason = reason;
		}

		@Override
		public void run() {
			gui.setStatus("Sorting...");

			try {
				dBrecords = persistence.getAllRecords();
				filterRecords = persistence.getAllFilters(reason);
			} catch (SQLException e) {
				logger.warn("Failed to load from database - {}", e.getMessage());
			}

			sorter.sortFilter(hammingDistance, reason, dBrecords, filterRecords);
			gui.setStatus("" + sorter.getNumberOfDuplicateGroups() + " Groups");
			List<Long> groups = sorter.getDuplicateGroups();
			gui.populateGroupList(groups);
		}
	}

	public JProgressBar getTotalProgress() {
		return producer.getTotalProgress();
	}
}
