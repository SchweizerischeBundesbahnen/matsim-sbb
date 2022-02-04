package ch.sbb.matsim.mavi.visum;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.SafeArray;
import com.jacob.com.Variant;
import java.util.List;
import org.apache.log4j.Logger;
import org.locationtech.jts.util.Assert;

public class Visum {

	private static final Logger log = Logger.getLogger(Visum.class);

	private final ActiveXComponent visum;

	public Visum(int version) {
		this.visum = new ActiveXComponent("Visum.Visum." + version);
		log.info("starting Visum client");
	}

	public static String[][] getArrayFromAttributeList(int n, ComObject object, String... attributes) {
		String[][] array = new String[n][attributes.length]; //2d array containing all attributes of all nodes
		int j = 0;
		for (String att : attributes) {
			SafeArray a = Dispatch.call(object.dispatch, "GetMultiAttValues", att).toSafeArray();
			int i = 0;
			while (i < n) {
				array[i][j] = a.getString(i, 1);
				i++;
			}
			j++;
		}
		return array;
	}

	public void loadVersion(String path) {
		log.info("loading version " + path);
		Dispatch.call(this.visum, "LoadVersion", new Variant(path));
		log.info("finished loading version");
	}

	public void setSettings() {
		Dispatch net = Dispatch.get(this.visum, "Net").toDispatch();

		log.info("Setting projection to CH1903_LV03");
		String value = "PROJCS[\"CH1903_LV03\",GEOGCS[\"GCS_CH1903\",DATUM[\"D_CH1903\",SPHEROID[\"Bessel_1841\",6377397.155,299.1528128]],PRIMEM[\"Greenwich\",0],UNIT[\"Degree\",0.0174532925199432955]],PROJECTION[\"Hotine_Oblique_Mercator_Azimuth_Center\"],PARAMETER[\"False_Easting\",600000],PARAMETER[\"False_Northing\",200000],PARAMETER[\"Scale_Factor\",1],PARAMETER[\"Azimuth\",90],PARAMETER[\"Longitude_Of_Center\",7.439583333333333],PARAMETER[\"Latitude_Of_Center\",46.95240555555556],UNIT[\"Meter\",1]]";
		//Dispatch.call(net, "SetProjection", value, true);

		// This is currently not working
		// Dispatch.call(net, "AttValue", "ConcatMaxLen").putDouble(99999);
		int length = (int) Dispatch.call(net, "AttValue", "ConcatMaxLen").getDouble();
		Assert.isTrue(length > 255, "Set the ConcatMaxLen manually in Visum: Network-> Network Settings -> Attributes -> Maximum text length");
	}

	public void filterForAngebot(String name) {
		Visum.ComObject filters = getComObject("Filters");
		Visum.ComObject filter = callComObject(filters, "LineGroupFilter");
		Visum.ComObject tpFilter = callComObject(filter, "TimeProfileFilter");
		initFilter(tpFilter);

		Dispatch.call(tpFilter.dispatch, "AddCondition", "OP_NONE", false,
				"LineRoute\\Line\\" + name, 9, 1, 0);
		Dispatch.put(filter.dispatch, "UseFilterForTimeProfiles", true);
		Dispatch.put(filter.dispatch, "UseFilterForTimeProfileItems", true);
		Dispatch.put(filter.dispatch, "UseFilterForVehJourneys", true);
	}

	public void setTimeProfilFilter(List<FilterCondition> condition) {
		Visum.ComObject filters = getComObject("Filters");
		Visum.ComObject filter = callComObject(filters, "LineGroupFilter");
		Visum.ComObject tpFilter = callComObject(filter, "TimeProfileFilter");
		initFilter(tpFilter);

		condition.forEach(c -> Dispatch.call(tpFilter.dispatch, "AddCondition", c.op, c.complement,
				c.attribute, c.comparator, c.val, c.position));
		Dispatch.put(filter.dispatch, "UseFilterForTimeProfiles", true);
		Dispatch.put(filter.dispatch, "UseFilterForTimeProfileItems", true);
		Dispatch.put(filter.dispatch, "UseFilterForVehJourneys", true);
	}

	public void setFilterCondition(ComObject filter, FilterCondition cond) {
		Dispatch.call(filter.dispatch, "AddCondition", cond.op, cond.complement,
				cond.attribute, cond.comparator, cond.val, cond.position);
	}

	public void initFilter(ComObject filter) {
		Dispatch.call(filter.dispatch, "Init");
	}

	public void useFilter(ComObject filter, boolean val) {
		Dispatch.put(filter.dispatch, "UseFilter", val);
	}

	public ComObject getComObject(String name) {
		return new ComObject(Dispatch.get(this.visum, name).toDispatch());
	}

	public ComObject callComObject(ComObject object, String name) {
		return new ComObject(Dispatch.call(object.dispatch, name).toDispatch());
	}

	public ComObject getComObject(ComObject object, String name) {
		return new ComObject(Dispatch.get(object.dispatch, name).toDispatch());
	}

	public ComObject getNetObject(String netObject) {
		ComObject net = getComObject("Net");
		return new ComObject(Dispatch.get(net.dispatch, netObject).toDispatch());
	}

	public static class ComObject {

		private final Dispatch dispatch;

		public ComObject(Dispatch dispatch) {
			this.dispatch = dispatch;
		}

		public int countActive() {
			return Dispatch.call(this.dispatch, "CountActive").getInt();
		}

		public void callMethod(String method, String... attributes) {
			Dispatch.call(this.dispatch, method, attributes);
		}

		public void callMethod(String method, String attribute, int dataType) {
			Dispatch.call(this.dispatch, method, attribute, dataType);
		}

		public int getNumActiveElements() {
			return Dispatch.call(this.dispatch, "NumActiveElements").getInt();
		}

		public SafeArray getSafeArray(String method) {
			return Dispatch.call(this.dispatch, method).toSafeArray();
		}
	}

	public static class FilterCondition {

		private final int position;
		private final String op;
		private final boolean complement;
		private final String attribute;
		private final int comparator;
		private final String val;

		public FilterCondition(int position, String op, boolean complement, String attribute, int comparator, String val) {
			this.position = position;
			this.op = op;
			this.comparator = comparator;
			this.complement = complement;
			this.attribute = attribute;
			this.val = val;
		}
	}
}