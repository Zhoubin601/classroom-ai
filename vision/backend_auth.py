"""Forward the authenticated local launcher identity without storing credentials."""
import os
from urllib.request import Request


def authenticated_request(url, method="GET"):
    request = Request(url, method=method)
    for header, variable in (("Authorization", "CLASSROOM_FACE_AUTHORIZATION"),
                             ("Cookie", "CLASSROOM_FACE_COOKIE")):
        value = os.environ.get(variable)
        if value:
            request.add_header(header, value)
    return request
