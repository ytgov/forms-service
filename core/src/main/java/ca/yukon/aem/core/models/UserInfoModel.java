package ca.yukon.aem.core.models;

import ca.yukon.aem.core.utils.UserInfoUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Model(adaptables = Resource.class)
public class UserInfoModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserInfoModel.class);

    @SlingObject
    private ResourceResolver resourceResolver;

    protected List<String> groups;

    private Map<String, String> propsMap = new HashMap<>();
    private ArrayList<String> xdpPaths = new ArrayList<>();
    private String sessionUserId = "";
    private String userPath = "";
    private boolean isLoggedIn = false;

    @PostConstruct
    protected void init() {
        try {
            Authorizable user = UserInfoUtils.getUser(resourceResolver);
            this.groups = UserInfoUtils.listGroups(resourceResolver);

            Session session = resourceResolver.adaptTo(Session.class);
            String userPath = user.getPath();

            if (session != null && session.nodeExists(userPath + "/profile")) {
                Node profileNode = session.getNode(userPath + "/profile");
                PropertyIterator properties = profileNode.getProperties();

                this.propsMap.put("path", userPath);
                while (properties.hasNext()) {
                    Property property = properties.nextProperty();
                    if (property.getName().equals("sling:resourceType") || property.getName().equals("jcr:primaryType")) {
                        continue;  // skip
                    }
                    if (!property.isMultiple()) {
                        this.propsMap.put(property.getName(), property.getString());
                    }
                }

                if (this.groups != null && this.groups.size() > 0) {
                    this.propsMap.put("groups", this.groups.toString());
                }
            }

            try {
                this.sessionUserId = user.getID();
                this.userPath = userPath;
            } catch (Exception e) {
                this.sessionUserId = "";
            }

            this.isLoggedIn =
                    StringUtils.isNotBlank(this.sessionUserId) && !StringUtils.equalsIgnoreCase(this.sessionUserId, "anonymous");
        } catch (Exception e) {
            LOGGER.error("Failed to get user information: ", e);
        }
    }

    public Map<String, String> getPropsMap() {
        return this.propsMap;
    }

    public ArrayList<String> getXdpPaths() {
        return xdpPaths;
    }

    public String getSessionUserId() {
        return sessionUserId;
    }

    public String getUserPath() {
        return userPath;
    }

    public boolean getIsLoggedIn() {
        return isLoggedIn;
    }

}
