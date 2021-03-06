package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.parsers.images.DoujinsParser;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Helper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class DoujinsContent implements ContentParser {
    @Selector(value = ".folder-title a")
    private List<Element> breadcrumbs;
    @Selector("img.doujin")
    private List<Element> images;
    @Selector(value = "a[href*='/artists/']")
    private List<Element> artists;
    @Selector(value = "a[href*='/searches?tag_id=']") // To deduplicate
    private List<Element> tags;


    public Content toContent(@Nonnull String url) {
        Content result = new Content();

        result.setSite(Site.DOUJINS);
        if (url.isEmpty()) return result.setStatus(StatusContent.IGNORED);

        result.setUrl(url.replace(Site.DOUJINS.getUrl(), ""));

        if (breadcrumbs != null && !breadcrumbs.isEmpty()) {
            Element e = breadcrumbs.get(breadcrumbs.size() - 1);
            result.setTitle(Helper.removeNonPrintableChars(e.text()));
        }

        if (images != null && !images.isEmpty()) {
            // Cover = thumb from the 1st page
            String coverUrl = images.get(0).attr("data-thumb2");
            result.setCoverImageUrl(coverUrl);
        }

        List<String> imageUrls = DoujinsParser.parseImages(images);
        result.setQtyPages(imageUrls.size());
        result.setImageFiles(ParseHelper.urlsToImageFiles(imageUrls, StatusContent.SAVED));

        // Deduplicate tags
        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, true, Site.DOUJINS);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.DOUJINS);
        result.addAttributes(attributes);

        return result;
    }
}
