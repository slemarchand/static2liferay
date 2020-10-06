import com.liferay.portal.scripting.groovy.context.*
import com.liferay.portal.kernel.json.*
import com.liferay.portal.kernel.service.*
import com.liferay.portal.kernel.model.*
import com.liferay.portal.kernel.util.WebKeys
import com.liferay.portal.kernel.util.FriendlyURLNormalizerUtil
import com.liferay.portal.kernel.util.Validator
import com.liferay.portal.kernel.util.PortalUtil
import com.liferay.portal.kernel.util.UnicodeProperties
import com.liferay.portal.kernel.workflow.WorkflowConstants
import com.liferay.asset.kernel.service.*
import com.liferay.journal.service.*
import com.liferay.petra.string.StringPool
import com.liferay.fragment.contributor.FragmentCollectionContributorTracker
import com.liferay.fragment.exception.NoSuchEntryException
import com.liferay.fragment.model.*
import com.liferay.fragment.service.*
import com.liferay.layout.util.structure.LayoutStructure
import com.liferay.layout.util.structure.LayoutStructureItem
import com.liferay.layout.page.template.model.*
import java.net.URL
import java.io.StringWriter
import java.io.PrintWriter
import static groovy.io.FileType.FILES;
import com.liferay.portal.scripting.groovy.context.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import com.liferay.segments.constants.SegmentsExperienceConstants
import com.liferay.layout.page.template.service.*
import com.liferay.layout.util.*
import org.osgi.framework.*

// JSoup: https://repo1.maven.org/maven2/org/jsoup/jsoup/1.13.1/jsoup-1.13.1.jar

/*---------- <CustomizationZone> ----------*/

INPUT_DIR = '/Users/dev24/TALK2/SennaJS/sennajs.com'

EXCLUDES = [
    '/api',
    '/examples'
]

def processLayoutContent(Layout layout, Document document) {
    
    content = document.getElementsByTag('body').first()

    addHTMLFragment(layout, content.toString())
}


/*---------- </CustomizationZone> ----------*/


def createSite(inputDir) {
    pathElements = inputDir.split('/')
    name = pathElements[pathElements.length - 1]
    nameMap = [(Locale.US): name]
    descriptionMap = null
    type = GroupConstants.TYPE_SITE_OPEN
    groovyScriptingContext = new GroovyScriptingContext()
    group = GroupLocalServiceUtil.addGroup(
        groovyScriptingContext.defaultUserId,
        GroupConstants.DEFAULT_PARENT_GROUP_ID, null, 0, 0, nameMap,
        descriptionMap, type, true,
        GroupConstants.DEFAULT_MEMBERSHIP_RESTRICTION, null, true, true,
        groovyScriptingContext.serviceContext);

     return group       
}

def processHtmlFile(f, group) {

    path = f.path.substring(INPUT_DIR.length(), f.path.length())

    if(EXCLUDES.any{ exclude -> path.startsWith(exclude)}) {
        return
    }

    content = f.text

    Document doc = Jsoup.parse(content);

    isHome = false

    if(path.endsWith('/index.html')) {
        friendlyURL = path.substring(0, path.length() - '/index.html'.length())
        if(friendlyURL.empty) {
            friendlyURL = '/home'
            isHome = true
        }
    } else {
        friendlyURL = path
    }

    title = friendlyURL

    headsElts = doc.getElementsByTag('head')
    if(headsElts.size() > 0) {
        titleElts = headsElts.first().getElementsByTag('title')
         if(titleElts.size() > 0) {
            title = titleElts.first().text()
         }
    }

    groupId = group.groupId
    privateLayout = false
    parentLayoutId = 0
    nameMap = [(Locale.US):title]
    titleMap = [(Locale.US):title]
    type = 'content'
    typeSettings = ''
    masterLayoutPlid = 0
    serviceContext = new ServiceContext()

    Layout layout = LayoutServiceUtil.addLayout(
        groupId, privateLayout, parentLayoutId, nameMap,
        new HashMap<>(), new HashMap<>(), new HashMap<>(),
        new HashMap<>(), type, typeSettings,
        false, masterLayoutPlid, new HashMap<>(), serviceContext);

    println(friendlyURL)

    processLayoutContent(layout.fetchDraftLayout(), doc)

    publishLayout(layout)
}

def addHTMLFragment(layout, html) {

    fragmentEntryKey = 'BASIC_COMPONENT-html'

    serviceContext = new GroovyScriptingContext().serviceContext

    scopeGroupId = layout.groupId

    plid = layout.plid

    fragmentEntryId = 0

    segmentsExperienceId = SegmentsExperienceConstants.ID_DEFAULT

    css = """
    .component-html img {
	    max-width: 100%;
    }
    """

    html = """
    <div class="component-html mb-\${configuration.marginBottom}" data-lfr-editable-id="element-html" data-lfr-editable-type="html">
        ${html}
    </div>
    """

    js = ''

    configuration = """
    {
        "fieldSets": [
            {
                "configurationRole": "style",
                "fields": [
                    {
                        "dataType": "string",
                        "defaultValue": "3",
                        "label": "margin-bottom",
                        "name": "marginBottom",
                        "type": "select",
                        "typeOptions": {
                            "validValues": [
                                {
                                    "value": "0"
                                },
                                {
                                    "value": "1"
                                },
                                {
                                    "value": "2"
                                },
                                {
                                    "value": "3"
                                },
                                {
                                    "value": "4"
                                }
                            ]
                        }
                    }
                ]
            }
        ]
    }
    """

    fragmentEntryLink = FragmentEntryLinkServiceUtil.addFragmentEntryLink(
        scopeGroupId, 0,
        fragmentEntryId, segmentsExperienceId,
        plid, css,
        html, js,
        configuration, null, StringPool.BLANK, 0,
        fragmentEntryKey, serviceContext)

    position = 0

    LayoutPageTemplateStructure layoutPageTemplateStructure =
			LayoutPageTemplateStructureLocalServiceUtil.
				fetchLayoutPageTemplateStructure(scopeGroupId, plid, true);

    LayoutStructure layoutStructure = LayoutStructure.of(
			layoutPageTemplateStructure.getData(segmentsExperienceId))

    JSONObject dataJSONObject = layoutStructure.toJSONObject();

    parentItemId = dataJSONObject.getJSONObject('rootItems').getString('main')

    layoutStructure.addFragmentLayoutStructureItem( 
						fragmentEntryLink.getFragmentEntryLinkId(),
						parentItemId, position);

    dataJSONObject = layoutStructure.toJSONObject();

    data = dataJSONObject.toString()

    fragmentEntryLinkId = fragmentEntryLink.getFragmentEntryLinkId()

    LayoutPageTemplateStructureServiceUtil.
			updateLayoutPageTemplateStructureData(
				scopeGroupId, plid, segmentsExperienceId, data);
}

def publishLayout(layout) {

    draftLayout = layout.fetchDraftLayout()

    layoutCopyHelper = getLayoutCopyHelper()

    layout = layoutCopyHelper.copyLayout(draftLayout, layout)

    layout.setType(draftLayout.getType())
    layout.setStatus(WorkflowConstants.STATUS_APPROVED)

    String layoutPrototypeUuid = layout.getLayoutPrototypeUuid()

    layout.setLayoutPrototypeUuid(null)

    LayoutLocalServiceUtil.updateLayout(layout)

    draftLayout = LayoutLocalServiceUtil.getLayout(draftLayout.getPlid())

    UnicodeProperties typeSettingsUnicodeProperties =
        draftLayout.getTypeSettingsProperties()

    if (Validator.isNotNull(layoutPrototypeUuid)) {
        typeSettingsUnicodeProperties.setProperty(
            "layoutPrototypeUuid", layoutPrototypeUuid)
    }

    draftLayout.setStatus(WorkflowConstants.STATUS_APPROVED)

    LayoutLocalServiceUtil.updateLayout(draftLayout)
}

def getLayoutCopyHelper() {
    BundleContext ctx = FrameworkUtil.getBundle(LayoutCopyHelper.class).getBundleContext()
    ServiceReference<?>[] refs = ctx.getServiceReferences(LayoutCopyHelper.class.getName(), null)
    ctx.getService(refs[0])
}

try {

    siteGroup = createSite(INPUT_DIR)

    new File(INPUT_DIR).eachFileRecurse(FILES) { f ->
       if(f.path.endsWith('.html')) {
           processHtmlFile(f, siteGroup)
       }
       
    }

} catch(e) {
    sw = new StringWriter()
    e.printStackTrace(new PrintWriter(sw))
    throw new Exception(sw.toString())
}