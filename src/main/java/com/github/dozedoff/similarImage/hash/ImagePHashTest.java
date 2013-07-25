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
package com.github.dozedoff.similarImage.hash;

import java.io.InputStream;

import magick.MagickException;

import org.junit.Before;
import org.junit.Test;

public class ImagePHashTest {
	private final String TEST_IMAGE_NAME = "testImage.jpg";

	InputStream is;
	String path;
	ImagePHash iph;

	@Before
	public void setUp() throws Exception {
		is = Thread.currentThread().getContextClassLoader().getResourceAsStream(TEST_IMAGE_NAME);
		path = Thread.currentThread().getContextClassLoader().getResource(TEST_IMAGE_NAME).toString();
		iph = new ImagePHash();
	}

	@Test
	public void testGetLongHashInputStream() throws Exception {
		long pHash = iph.getLongHash(is);
	}

	@Test
	public void testImageMagickConvert() throws MagickException {
		long pHash = iph.ImageMagickConvert(path);
	}
}
