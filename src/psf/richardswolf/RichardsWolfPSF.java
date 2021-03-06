/**
 * PSFGenerator
 * 
 * Authors: Daniel Sage and Hagai Kirshner
 * Organization: Biomedical Imaging Group (BIG), Ecole Polytechnique Federale de Lausanne
 * Address: EPFL-STI-IMT-LIB, 1015 Lausanne, Switzerland
 * Information: http://bigwww.epfl.ch/algorithms/psfgenerator/
 *
 * References:
 * [1] H. Kirshner, F. Aguet, D. Sage, M. Unser
 * 3-D PSF Fitting for Fluorescence Microscopy: Implementation and Localization Application 
 * Journal of Microscopy, vol. 249, no. 1, pp. 13-25, January 2013.
 * Available at: http://bigwww.epfl.ch/publications/kirshner1301.html
 * 
 * [2] A. Griffa, N. Garin, D. Sage
 * Comparison of Deconvolution Software in 3D Microscopy: A User Point of View
 * G.I.T. Imaging & Microscopy, vol. 12, no. 1, pp. 43-45, March 2010.
 * Available at: http://bigwww.epfl.ch/publications/griffa1001.html
 *
 * Conditions of use:
 * Conditions of use: You are free to use this software for research or
 * educational purposes. In addition, we expect you to include adequate
 * citations and acknowledgments whenever you present or publish results that
 * are based on it.
 */

/**
 * Copyright 2010-2017 Biomedical Imaging Group at the EPFL.
 * 
 * This file is part of PSFGenerator.
 * 
 * PSFGenerator is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * PSFGenerator is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * PSFGenerator. If not, see <http://www.gnu.org/licenses/>.
 */

package psf.richardswolf;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import bilib.commons.components.GridToolbar;
import bilib.commons.components.SpinnerRangeDouble;
import bilib.commons.job.runnable.Job;
import bilib.commons.job.runnable.Pool;
import bilib.commons.settings.Settings;
import psf.PSF;

public class RichardsWolfPSF extends PSF {

	private double				ni_Default	= 1.5;

	private SpinnerRangeDouble	spnNI		= new SpinnerRangeDouble(ni_Default, 0, 3, 0.1, 5);
	private JComboBox			cmbAccuracy	= new JComboBox(new String[] { "Good", "Better", "Best" });

	private double				ni			= 1.5;
	private int					accuracy	= 0;

	public RichardsWolfPSF() {
		fullname = "Richards & Wolf 3D Optical Model";
		shortname = "RW";
	}

	@Override
	public String getDescription() {
		String desc = "<h1>" + fullname + "</h1>";
		desc += "<p>This model describes the vectorial-based. ";
		desc += "The phase aberration is given by the the Gibson & Lanni model. ";
		desc += "The 3 electric field components are evaluated independently.</p>";
		return desc;
	}

	@Override
	public String checkSize(int nx, int ny, int nz) {
		if (nz < 3)
			return ("nz should be greater than 3.");
		if (nx < 4)
			return ("nx should be greater than 4.");
		if (ny < 4)
			return ("ny should be greater than 4.");
		return "";
	}

	@Override
	public void resetParameters() {
		spnNI.set(ni_Default);
		cmbAccuracy.setSelectedIndex(0);
	}

	@Override
	public void fetchParameters() {
		ni = spnNI.get();
		accuracy = cmbAccuracy.getSelectedIndex();
	}

	@Override
	public JPanel buildPanel(Settings settings) {
		GridToolbar pn = new GridToolbar(false);
		pn.place(03, 0, "<html>Refractive index immersion</html>");
		pn.place(06, 0, "<html>Accuracy computation</html>");

		pn.place(03, 1, spnNI);
		pn.place(06, 1, cmbAccuracy);

		pn.place(03, 2, new JLabel("<html>ni</html>"));
		JPanel panel = new JPanel();
		panel.add(pn);
		settings.record("psf-" + shortname + "-NI", spnNI, "" + ni_Default);
		settings.record("psf-" + shortname + "-accuracy", cmbAccuracy, (String) cmbAccuracy.getItemAt(0));
		return panel;
	}

	@Override
	public void generate(Pool pool) {
		for (int z = 0; z < nz; z++) {
			double defocus = resAxial * 1E-9 * (z - (nz - 1.0) / 2.0);
			RichardsWolf plane = new RichardsWolf(z, defocus);
			plane.addMonitor(this);
			pool.register(plane);
		}
	}

	public class RichardsWolf extends Job {

		private int		OVER_SAMPLING	= 2;
		private int		z;
		private double	defocus;

		public RichardsWolf(int z, double defocus) {
			this.defocus = defocus;
			this.z = z;
		}

		@Override
		public void process() {

			// The center of the image in units of [pixels]
			double x0 = (nx - 1) / 2.0;
			double y0 = (ny - 1) / 2.0;

			// Lateral particle position in units of [pixels]
			double xp = x0;// 0.0/p.pixelSize;
			double yp = y0;// 0.0/p.pixelSize;

			// Radial locations.
			// double xpAbs = Math.abs(xp), ypAbs = Math.abs(yp);
			// double maxRadialDistanceInPixels =
			// Math.round(Math.sqrt((xpAbs+nx-x0)*(xpAbs+nx-x0)+(ypAbs+ny-y0)*(ypAbs+ny-y0)))+1;
			int maxRadius = ((int) Math.round(Math.sqrt((nx - x0) * (nx - x0) + (ny - y0) * (ny - y0)))) + 1;
			double[] r = new double[maxRadius * OVER_SAMPLING];
			double[] h = new double[r.length];

			KirchhoffDiffractionSimpson I = new KirchhoffDiffractionSimpson(defocus, ni, accuracy, NA, lambda);
			// KirchhoffDiffraction I = new KirchhoffDiffraction(p,accuracy);
			for (int n = 0; n < r.length; n++) {
				r[n] = ((double) n) / ((double) OVER_SAMPLING);
				// h[n] = I.calculate(r[n] * p.lateralResolution);
				h[n] = I.calculate(r[n] * resLateral * 1E-9);
				if (!live)
					return;
			}

			// Linear interpolation of the pixels values
			double[] slice = new double[nx * ny];
			double rPixel, value;
			int index;
			for (int x = 0; x < nx; x++) {
				for (int y = 0; y < ny; y++) {
					rPixel = Math.sqrt((x - xp) * (x - xp) + (y - yp) * (y - yp));
					index = (int) Math.floor(rPixel * OVER_SAMPLING);
					value = h[index] + (h[index + 1] - h[index]) * (rPixel - r[index]) * OVER_SAMPLING;

					slice[x + nx * y] = value;
				}
				if (!live)
					return;
			}

			data.data[z] = slice;
			increment(90.0 / nz, "" + z + " / " + nz);
		}
	}
}
